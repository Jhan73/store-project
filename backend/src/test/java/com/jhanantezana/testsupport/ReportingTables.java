package com.jhanantezana.testsupport;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class ReportingTables {

	private ReportingTables() {
	}

	public static void clean(JdbcClient jdbc) {
		jdbc.sql("delete from reporting.sales_line").update();
		jdbc.sql("delete from reporting.exception_event").update();
		jdbc.sql("delete from reporting.shift_summary").update();
		jdbc.sql("delete from reporting.shift_summary_delivery").update();
	}

}
