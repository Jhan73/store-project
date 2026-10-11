package com.jhanantezana.jugueria.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StockTrackingAvailabilityIT {

	@Autowired
	StockService stock;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void untrackingAProductWithNothingToSellMakesItAvailableAgain() {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();

		stock.track(product, false, 0);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void untrackingAProductThatStaffHidAmongStockLeavesItHidden() {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, 3, null);
		hide(product);

		stock.track(product, false, 0);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
	}

	@Test
	void trackingAProductThatHasStockMakesItAvailable() {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, 3, null);
		stock.track(product, false, 0);
		hide(product);

		stock.track(product, true, 0);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void changingTheThresholdOfATrackedProductKeepsAManualHide() {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, 3, null);
		hide(product);

		stock.track(product, true, 5);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
	}

	@Test
	void trackingAProductWithNothingToSellStillMakesItUnavailable() {
		var product = InventoryTables.aProduct(jdbc);

		stock.track(product, true, 0);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
	}

	private void hide(UUID product) {
		jdbc.sql("update catalog.product set available = false where id = ?").param(product).update();
	}

}
