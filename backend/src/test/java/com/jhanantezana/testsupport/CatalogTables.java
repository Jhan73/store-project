package com.jhanantezana.testsupport;

import org.springframework.jdbc.core.simple.JdbcClient;

// Integration tests are not transactional, so each one wipes what it wrote; the seeded default station stays.
public final class CatalogTables {

	private CatalogTables() {
	}

	public static void clean(JdbcClient jdbc) {
		jdbc.sql("delete from catalog.product").update();
		jdbc.sql("delete from catalog.category").update();
		jdbc.sql("delete from catalog.modifier_group").update();
		jdbc.sql("delete from catalog.station where not default_station").update();
	}

}
