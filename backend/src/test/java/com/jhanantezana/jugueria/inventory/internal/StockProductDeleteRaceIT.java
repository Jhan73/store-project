package com.jhanantezana.jugueria.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.InventoryTables;

// A product delete holds the product row while it checks for stock history; a stock write must wait for it instead of
// recording a movement for a product that is about to disappear.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StockProductDeleteRaceIT {

	@Autowired
	StockService stock;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	final ExecutorService executor = Executors.newFixedThreadPool(2);

	@AfterEach
	void cleanUp() {
		executor.shutdownNow();
		InventoryTables.clean(jdbc);
	}

	@Test
	void anEntryWaitsForAProductBeingDeletedAndThenFindsNothing() throws Exception {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);

		assertWaitsForTheDeleteAndFails(product, id -> stock.recordEntry(id, 5, null));
		assertThat(movements(product)).isZero();
	}

	@Test
	void anAdjustmentWaitsForAProductBeingDeletedAndThenFindsNothing() throws Exception {
		var product = InventoryTables.aProduct(jdbc);
		InventoryTables.anAdjustmentReason(jdbc, "damaged");
		stock.track(product, true, 0);

		assertWaitsForTheDeleteAndFails(product, id -> stock.adjust(id, 5, "damaged", null));
		assertThat(movements(product)).isZero();
	}

	@Test
	void trackingWaitsForAProductBeingDeletedAndLeavesNoStockItem() throws Exception {
		var product = InventoryTables.aProduct(jdbc);

		assertWaitsForTheDeleteAndFails(product, id -> stock.track(id, true, 0));
		assertThat(jdbc.sql("select count(*) from inventory.stock_item where product_id = ?")
			.param(product)
			.query(Long.class)
			.single()).isZero();
	}

	private void assertWaitsForTheDeleteAndFails(UUID product, Consumer<UUID> write) throws Exception {
		var locked = new CountDownLatch(1);
		var finishDelete = new CountDownLatch(1);
		var deleting = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
			jdbc.sql("select id from catalog.product where id = ? for update").param(product).query(UUID.class).list();
			locked.countDown();
			waitFor(finishDelete);
			jdbc.sql("delete from inventory.stock_item where product_id = ?").param(product).update();
			jdbc.sql("delete from catalog.product where id = ?").param(product).update();
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		Future<?> writing = executor.submit(() -> write.accept(product));
		await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(5)).until(() -> !writing.isDone());
		finishDelete.countDown();

		deleting.get(10, TimeUnit.SECONDS);
		assertThatThrownBy(() -> writing.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(BusinessException.class);
	}

	private long movements(UUID product) {
		return jdbc.sql("select count(*) from inventory.stock_movement where product_id = ?")
			.param(product)
			.query(Long.class)
			.single();
	}

	private static void waitFor(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
