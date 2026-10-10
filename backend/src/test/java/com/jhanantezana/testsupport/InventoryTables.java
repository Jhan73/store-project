package com.jhanantezana.testsupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

// stock_movement is append-only, so role app cannot delete it: tests give every product a fresh id and read the
// ledger by product instead of wiping it.
public final class InventoryTables {

	private InventoryTables() {
	}

	public static void clean(JdbcClient jdbc) {
		jdbc.sql("delete from inventory.stock_reservation").update();
		jdbc.sql("delete from inventory.stock_item").update();
		jdbc.sql("delete from store.reason where type = 'STOCK_ADJUSTMENT'").update();
		CatalogTables.clean(jdbc);
	}

	/** A sellable product, inserted directly so the test needs neither the catalog service nor an HTTP call. */
	public static UUID aProduct(JdbcClient jdbc) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		var categoryId = UUID.randomUUID();
		jdbc.sql("""
				insert into catalog.category (id, station_id, name, display_order, active, created_at, updated_at)
				select :id, id, :name, 0, true, :now, :now from catalog.station where default_station
				""").param("id", categoryId).param("name", "Cat-" + categoryId).param("now", now).update();
		var productId = UUID.randomUUID();
		jdbc.sql("""
				insert into catalog.product (id, category_id, name, price_amount, price_currency, created_at, updated_at)
				values (:id, :category, :name, 5.00, 'PEN', :now, :now)
				""")
			.param("id", productId)
			.param("category", categoryId)
			.param("name", "Bottle-" + productId)
			.param("now", now)
			.update();
		return productId;
	}

	public static boolean isAvailable(JdbcClient jdbc, UUID productId) {
		return jdbc.sql("select available from catalog.product where id = ?")
			.param(productId)
			.query(Boolean.class)
			.single();
	}

	/** The stock adjustment reasons an admin picks from; {@link #clean} removes them again. */
	public static void anAdjustmentReason(JdbcClient jdbc, String code) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.sql("""
				insert into store.reason (id, type, code, active, created_at, updated_at)
				values (:id, 'STOCK_ADJUSTMENT', :code, true, :now, :now) on conflict do nothing
				""").param("id", UUID.randomUUID()).param("code", code).param("now", now).update();
	}

}
