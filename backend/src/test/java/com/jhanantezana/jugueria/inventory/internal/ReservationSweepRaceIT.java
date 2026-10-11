package com.jhanantezana.jugueria.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.InventoryTables;

// Real threads and connections against real PostgreSQL: the sweep, commit and reserve meet in the same rows.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class ReservationSweepRaceIT {

	static final Duration TTL = Duration.ofMinutes(15);

	@Autowired
	InventoryApi inventory;

	@Autowired
	StockService stock;

	@Autowired
	ReservationExpiry expiry;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	// The sweep locks a reservation row, then stock rows; a re-reserve must not hold a stock row while it waits on one.
	@Test
	void aReReserveWaitingOnTheSweepHoldsNoStockRowTheSweepNeeds() throws Exception {
		var products = twoTrackedProducts();
		var low = products.get(0);
		var high = products.get(1);
		var other = UUID.randomUUID();
		var order = UUID.randomUUID();
		inventory.reserve(other, List.of(new StockLine(low, 1)), TTL);
		inventory.reserve(order, List.of(new StockLine(high, 1)), TTL);

		try (var sweep = dataSource.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
			sweep.setAutoCommit(false);
			try (var statement = sweep.createStatement()) {
				statement.executeUpdate("UPDATE inventory.stock_reservation SET status = 'EXPIRED' "
						+ "WHERE reference_id IN ('" + other + "', '" + order + "')");
				Future<?> reReserve = pool.submit(() -> inventory.reserve(order,
						List.of(new StockLine(low, 1), new StockLine(high, 1)), TTL));
				await().atMost(Duration.ofSeconds(20)).until(this::someoneWaitsOnALock);

				statement.executeUpdate("UPDATE inventory.stock_item SET reserved = reserved - 1 WHERE product_id = '"
						+ low + "'");
				statement.executeUpdate("UPDATE inventory.stock_item SET reserved = reserved - 1 WHERE product_id = '"
						+ high + "'");
				sweep.commit();

				reReserve.get();
			}
			catch (SQLException deadlock) {
				sweep.rollback();
				throw deadlock;
			}
		}

		assertThat(reserved(low)).isEqualTo(1);
		assertThat(reserved(high)).isEqualTo(1);
	}

	@Test
	void parallelSweepsAndReReservesOfMultiLineReferencesNeverFail() throws Exception {
		var products = twoTrackedProducts();
		var low = products.get(0);
		var high = products.get(1);
		var rounds = 12;
		for (var round = 0; round < rounds; round++) {
			var order = UUID.randomUUID();
			var lines = List.of(new StockLine(low, 1), new StockLine(high, 1));
			inventory.reserve(order, lines, TTL);
			lapse(order);

			var results = inParallel(List.of(() -> expiry.expireBatch(), () -> {
				inventory.reserve(order, lines, TTL);
				return 0;
			}));

			assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
			for (var product : products) {
				assertLedgerExplainsStock(product);
			}
			jdbc.sql("update inventory.stock_reservation set expires_at = now() - interval '1 minute' "
					+ "where reference_id = ?").param(order).update();
			expiry.expireBatch();
		}

		assertThat(reserved(low)).isZero();
		assertThat(reserved(high)).isZero();
	}

	@Test
	void aCommitAndTheSweepOfOneLapsedReservationApplyExactlyOne() throws Exception {
		var product = trackedWithStock(40);
		var orders = new ArrayList<UUID>();
		for (var i = 0; i < 20; i++) {
			var order = UUID.randomUUID();
			inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
			lapse(order);
			orders.add(order);
		}

		var work = new ArrayList<Callable<Integer>>();
		work.add(() -> expiry.expireBatch());
		for (var order : orders) {
			work.add(() -> {
				inventory.commit(order);
				return 0;
			});
		}
		var results = inParallel(work);

		assertThat(results.stream().filter(result -> result.failure() != null)).allSatisfy(result -> assertThat(
				result.failure()).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode().code()).isEqualTo("inventory.reservation-not-active")));
		assertThat(reserved(product)).isZero();
		var committed = movements(product, "COMMIT");
		var expired = movements(product, "EXPIRE");
		assertThat(committed + expired).isEqualTo(20);
		assertThat(onHand(product)).isEqualTo(40 - (int) committed);
		assertLedgerExplainsStock(product);
	}

	private boolean someoneWaitsOnALock() {
		return jdbc.sql("select count(*) from pg_stat_activity where wait_event_type = 'Lock'")
			.query(Long.class)
			.single() > 0;
	}

	private List<UUID> twoTrackedProducts() {
		var products = new ArrayList<>(List.of(trackedWithStock(50), trackedWithStock(50)));
		products.sort(Comparator.comparing(UUID::toString));
		return products;
	}

	private UUID trackedWithStock(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private void lapse(UUID order) {
		jdbc.sql("update inventory.stock_reservation set expires_at = now() - interval '1 minute' where reference_id = ?")
			.param(order)
			.update();
	}

	private record Result(Throwable failure) {
	}

	private List<Result> inParallel(List<Callable<Integer>> work) throws Exception {
		var ready = new CountDownLatch(work.size());
		var go = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(work.size())) {
			var futures = new ArrayList<Future<Result>>();
			for (var task : work) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					try {
						task.call();
						return new Result(null);
					}
					catch (Throwable t) {
						return new Result(t);
					}
				}));
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
