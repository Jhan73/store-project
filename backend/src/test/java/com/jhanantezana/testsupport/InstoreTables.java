package com.jhanantezana.testsupport;

import org.springframework.jdbc.core.simple.JdbcClient;

// Integration tests are not transactional, so each one wipes what it wrote.
public final class InstoreTables {

	private InstoreTables() {
	}

	public static void clean(JdbcClient jdbc) {
		jdbc.sql("delete from instore.dining_table").update();
	}

}
