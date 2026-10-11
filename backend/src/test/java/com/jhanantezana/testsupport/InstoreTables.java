package com.jhanantezana.testsupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

// Integration tests are not transactional, so each one wipes what it wrote.
public final class InstoreTables {

	/** A product with a required size group: the options are priced 0.00 and 3.00 over the product's 5.00. */
	public record SizedProduct(UUID productId, UUID small, UUID large) {
	}

	private InstoreTables() {
	}

	public static void clean(JdbcClient jdbc) {
		jdbc.sql("delete from instore.ticket_line").update();
		jdbc.sql("delete from instore.ticket").update();
		jdbc.sql("delete from instore.dining_table").update();
	}

	/** Cleans what tickets reach into: the board, the catalog and the void reasons. */
	public static void cleanAll(JdbcClient jdbc) {
		clean(jdbc);
		PreparationTables.clean(jdbc);
		jdbc.sql("delete from store.reason where type in ('VOID', 'COMP')").update();
		CatalogTables.clean(jdbc);
	}

	public static UUID aTable(JdbcClient jdbc) {
		var id = UUID.randomUUID();
		return aTable(jdbc, "Table-" + id);
	}

	public static UUID aTable(JdbcClient jdbc, String name) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		var id = UUID.randomUUID();
		jdbc.sql("""
				insert into instore.dining_table (id, name, display_order, active, created_at, updated_at)
				values (:id, :name, 0, true, :now, :now)
				""").param("id", id).param("name", name).param("now", now).update();
		return id;
	}

	public static void deactivate(JdbcClient jdbc, UUID tableId) {
		jdbc.sql("update instore.dining_table set active = false where id = ?").param(tableId).update();
	}

	public static UUID aProduct(JdbcClient jdbc) {
		return InventoryTables.aProduct(jdbc);
	}

	public static SizedProduct aSizedProduct(JdbcClient jdbc) {
		var product = InventoryTables.aProduct(jdbc);
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		var group = UUID.randomUUID();
		var small = UUID.randomUUID();
		var large = UUID.randomUUID();
		jdbc.sql("""
				insert into catalog.modifier_group (id, name, required, min_choices, max_choices, created_at, updated_at)
				values (:id, :name, true, 1, 1, :now, :now)
				""").param("id", group).param("name", "Size-" + group).param("now", now).update();
		option(jdbc, small, group, "Small", "0.00", 0);
		option(jdbc, large, group, "Large", "3.00", 1);
		jdbc.sql("insert into catalog.product_modifier_group (product_id, display_order, group_id) values (?, 0, ?)")
			.param(product)
			.param(group)
			.update();
		return new SizedProduct(product, small, large);
	}

	private static void option(JdbcClient jdbc, UUID id, UUID group, String name, String delta, int order) {
		jdbc.sql("""
				insert into catalog.modifier_option (id, group_id, name, price_delta_amount, price_delta_currency,
					display_order, available)
				values (:id, :group, :name, :delta::numeric, 'PEN', :order, true)
				""")
			.param("id", id)
			.param("group", group)
			.param("name", name)
			.param("delta", delta)
			.param("order", order)
			.update();
	}

	public static void aVoidReason(JdbcClient jdbc, String code) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.sql("""
				insert into store.reason (id, type, code, active, created_at, updated_at)
				values (:id, 'VOID', :code, true, :now, :now) on conflict do nothing
				""").param("id", UUID.randomUUID()).param("code", code).param("now", now).update();
	}

	public static String ticketStatus(JdbcClient jdbc, UUID ticketId) {
		return jdbc.sql("select status from instore.ticket where id = ?").param(ticketId).query(String.class).single();
	}

	public static String lineState(JdbcClient jdbc, UUID lineId) {
		return jdbc.sql("select state from instore.ticket_line where id = ?").param(lineId).query(String.class).single();
	}

	public static long count(JdbcClient jdbc, String table) {
		return jdbc.sql("select count(*) from instore." + table).query(Long.class).single();
	}

}
