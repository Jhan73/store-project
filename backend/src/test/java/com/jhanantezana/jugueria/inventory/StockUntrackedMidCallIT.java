package com.jhanantezana.jugueria.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.testsupport.InventoryTables;

// A product untracked while a sale is in flight is simply not tracked for that sale: no rejection, no ledger row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StockUntrackedMidCallIT {

	@Autowired
	InventoryApi inventory;

	@Autowired
	StockService stock;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void aSaleOfAProductBeingUntrackedIsIgnoredInsteadOfRejected() throws Exception {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, 5, null);
		var sale = UUID.randomUUID();

		try (var admin = dataSource.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
			admin.setAutoCommit(false);
			try (var untrack = admin.createStatement()) {
				untrack.executeUpdate("UPDATE inventory.stock_item SET tracked = false WHERE product_id = '" + product
						+ "'");
				var deduct = pool.submit(() -> inventory.deduct(sale, List.of(new StockLine(product, 1))));
				await().atMost(Duration.ofSeconds(20)).until(this::someoneWaitsOnALock);
				admin.commit();

				deduct.get();
			}
			finally {
				admin.rollback();
			}
		}

		assertThat(stock.get(product).onHand()).isEqualTo(5);
		assertThat(jdbc.sql("select count(*) from inventory.stock_movement where product_id = ? and type = 'SALE'")
			.param(product)
			.query(Long.class)
			.single()).isZero();
	}

	private boolean someoneWaitsOnALock() {
		return jdbc.sql("select count(*) from pg_stat_activity where wait_event_type = 'Lock'")
			.query(Long.class)
			.single() > 0;
	}

}
