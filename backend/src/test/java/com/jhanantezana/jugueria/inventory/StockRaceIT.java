package com.jhanantezana.jugueria.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.InventoryTables;

// Real threads against real PostgreSQL: the guarantees live in the conditional updates, so nothing is mocked.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StockRaceIT {

	static final Duration TTL = Duration.ofMinutes(15);

	@Autowired
	InventoryApi inventory;

	@Autowired
	StockService stock;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void parallelSaleOfTheLastUnitSellsItOnce() throws Exception {
		var product = trackedWithStock(1);

		var results = inParallel(16, i -> () -> inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1))));

		assertThat(successes(results)).isEqualTo(1);
		assertThat(rejectionCodes(results)).containsOnly("inventory.insufficient-stock");
		assertThat(onHand(product)).isZero();
		assertThat(movements(product, "SALE")).isEqualTo(1);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		assertLedgerExplainsStock(product);
	}

	@Test
	void anOnlineReservationAndAnInStoreSaleNeverTakeTheSameLastUnit() throws Exception {
		var product = trackedWithStock(1);

		var results = inParallel(16, i -> i % 2 == 0
				? () -> inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 1)), TTL)
				: () -> inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1))));

		assertThat(successes(results)).isEqualTo(1);
		assertThat(rejectionCodes(results)).containsOnly("inventory.insufficient-stock");
		assertThat(onHand(product) - reserved(product)).isZero();
		assertLedgerExplainsStock(product);
	}

	@Test
	void parallelReservationsHoldNoMoreThanTheShelfHas() throws Exception {
		var product = trackedWithStock(5);

		var results = inParallel(20, i -> () -> inventory.reserve(UUID.randomUUID(),
				List.of(new StockLine(product, 1)), TTL));

		assertThat(successes(results)).isEqualTo(5);
		assertThat(reserved(product)).isEqualTo(5);
		assertThat(onHand(product)).isEqualTo(5);
		assertLedgerExplainsStock(product);
	}

	@Test
	void parallelReserveAndReleaseCyclesLeaveNothingHeld() throws Exception {
		var product = trackedWithStock(3);

		var results = inParallel(24, i -> () -> {
			var reference = UUID.randomUUID();
			try {
				inventory.reserve(reference, List.of(new StockLine(product, 1)), TTL);
			}
			catch (BusinessException rejected) {
				return;
			}
			inventory.release(reference);
		});

		assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
		assertThat(reserved(product)).isZero();
		assertThat(onHand(product)).isEqualTo(3);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
		assertLedgerExplainsStock(product);
	}

	@Test
	void aParallelCommitAndReleaseOfOneReservationApplyExactlyOne() throws Exception {
		var product = trackedWithStock(2);
		var reference = UUID.randomUUID();
		inventory.reserve(reference, List.of(new StockLine(product, 2)), TTL);

		var results = inParallel(8, i -> i % 2 == 0 ? () -> inventory.commit(reference)
				: () -> inventory.release(reference));

		assertThat(rejectionCodes(results)).isSubsetOf("inventory.reservation-not-active");
		assertThat(reserved(product)).isZero();
		assertThat(onHand(product)).isIn(0, 2);
		assertThat(movements(product, "COMMIT") + movements(product, "RELEASE")).isEqualTo(1);
		assertLedgerExplainsStock(product);
	}

	@Test
	void reservingSeveralProductsInOppositeOrdersNeverDeadlocks() throws Exception {
		var first = trackedWithStock(40);
		var second = trackedWithStock(40);

		var results = inParallel(32, i -> () -> inventory.reserve(UUID.randomUUID(),
				i % 2 == 0 ? List.of(new StockLine(first, 1), new StockLine(second, 1))
						: List.of(new StockLine(second, 1), new StockLine(first, 1)),
				TTL));

		assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
		assertThat(reserved(first)).isEqualTo(32);
		assertThat(reserved(second)).isEqualTo(32);
		assertLedgerExplainsStock(first);
		assertLedgerExplainsStock(second);
	}

	@Test
	void aSaleOfSeveralProductsThatCannotBeFilledChangesNothing() throws Exception {
		var plenty = trackedWithStock(10);
		var scarce = trackedWithStock(1);
		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(scarce, 1)));

		var results = inParallel(8, i -> () -> inventory.deduct(UUID.randomUUID(),
				List.of(new StockLine(plenty, 1), new StockLine(scarce, 1))));

		assertThat(successes(results)).isZero();
		assertThat(onHand(plenty)).isEqualTo(10);
		assertLedgerExplainsStock(plenty);
		assertLedgerExplainsStock(scarce);
	}

	private UUID trackedWithStock(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private record Result(@org.jspecify.annotations.Nullable Throwable failure, boolean sold) {
	}

	private interface Work {

		void run() throws Exception;

	}

	private List<Result> inParallel(int threads, java.util.function.IntFunction<Work> work) throws Exception {
		var ready = new CountDownLatch(threads);
		var go = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(threads)) {
			var futures = new ArrayList<Future<Result>>();
			for (var i = 0; i < threads; i++) {
				var task = work.apply(i);
				Callable<Result> call = () -> {
					ready.countDown();
					go.await();
					try {
						task.run();
						return new Result(null, true);
					}
					catch (Throwable t) {
						return new Result(t, false);
					}
				};
				futures.add(pool.submit(call));
			}
			ready.await();
			go.countDown();
			var results = new ArrayList<Result>();
			for (var future : futures) {
				results.add(future.get());
			}
			return results;
		}
	}

	private static long successes(List<Result> results) {
		return results.stream().filter(Result::sold).count();
	}

	// A lost race must surface as the business rejection, never as a deadlock or constraint violation.
	private static List<String> rejectionCodes(List<Result> results) {
		return results.stream()
			.filter(result -> !result.sold())
			.map(result -> result.failure() instanceof BusinessException e ? e.errorCode().code()
					: String.valueOf(result.failure()))
			.toList();
	}

	private int onHand(UUID product) {
		return jdbc.sql("select on_hand from inventory.stock_item where product_id = ?")
			.param(product)
			.query(Integer.class)
			.single();
	}

	private int reserved(UUID product) {
		return jdbc.sql("select reserved from inventory.stock_item where product_id = ?")
			.param(product)
			.query(Integer.class)
			.single();
	}

	private long movements(UUID product, String type) {
		return jdbc.sql("select count(*) from inventory.stock_movement where product_id = ? and type = ?")
			.param(product)
			.param(type)
			.query(Long.class)
			.single();
	}

	private void assertLedgerExplainsStock(UUID product) {
		var ledger = jdbc.sql("""
				select coalesce(sum(on_hand_delta), 0), coalesce(sum(reserved_delta), 0)
				from inventory.stock_movement where product_id = ?
				""").param(product).query((rs, row) -> new int[] { rs.getInt(1), rs.getInt(2) }).single();
		assertThat(ledger[0]).as("on hand explained by the ledger").isEqualTo(onHand(product));
		assertThat(ledger[1]).as("reserved explained by the ledger").isEqualTo(reserved(product));
	}

}
