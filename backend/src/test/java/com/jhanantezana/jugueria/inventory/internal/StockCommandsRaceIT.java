package com.jhanantezana.jugueria.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.UUID;
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
import com.jhanantezana.jugueria.shared.RequestHash;
import com.jhanantezana.testsupport.InventoryTables;

// The same request sent several times at once (a double click, a client retry) must move the stock once.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StockCommandsRaceIT {

	@Autowired
	StockCommands commands;

	@Autowired
	StockService stock;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void parallelDuplicatesOfOneEntryAddTheUnitsOnce() throws Exception {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var hash = RequestHash.of("POST", "/entries/" + product, 5);
		var threads = 8;
		var ready = new CountDownLatch(threads);
		var go = new CountDownLatch(1);

		try (var pool = Executors.newFixedThreadPool(threads)) {
			var futures = new ArrayList<Future<StockCommands.Outcome>>();
			for (var i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return commands.recordEntry(actor, key, hash, product, 5, null);
				}));
			}
			ready.await();
			go.countDown();
			var outcomes = new ArrayList<StockCommands.Outcome>();
			for (var future : futures) {
				outcomes.add(future.get());
			}

			assertThat(outcomes).filteredOn(outcome -> !outcome.replayed()).hasSize(1);
			assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.item().onHand()).isEqualTo(5));
		}
		assertThat(stock.get(product).onHand()).isEqualTo(5);
		assertThat(jdbc.sql("select count(*) from inventory.stock_movement where product_id = ?")
			.param(product)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

}
