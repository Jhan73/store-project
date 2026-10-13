package com.jhanantezana.jugueria.instore;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class InstoreAppendOnlyGrantsIT {

	record Privileges(boolean canInsert, boolean canSelect, boolean canUpdate, boolean canDelete,
			boolean canTruncate) {
	}

	@Autowired
	DataSource dataSource;

	@ParameterizedTest
	@ValueSource(strings = { "in_store_payment", "payment_line", "payment_void", "sale_void", "cash_movement" })
	void appMayInsertAndSelectButNeverUpdateDeleteOrTruncateMoneyRecords(String table) {
		var privileges = privilegesOn("instore." + table);

		assertThat(privileges.canInsert()).as("INSERT").isTrue();
		assertThat(privileges.canSelect()).as("SELECT").isTrue();
		assertThat(privileges.canUpdate()).as("UPDATE").isFalse();
		assertThat(privileges.canDelete()).as("DELETE").isFalse();
		assertThat(privileges.canTruncate()).as("TRUNCATE").isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "register_shift" })
	void aShiftIsClosedByUpdateSoItsTableStaysWritable(String table) {
		var privileges = privilegesOn("instore." + table);

		assertThat(privileges.canInsert()).isTrue();
		assertThat(privileges.canUpdate()).isTrue();
	}

	private Privileges privilegesOn(String table) {
		return JdbcClient.create(dataSource).sql("""
				SELECT has_table_privilege('app', :table, 'INSERT')   AS can_insert,
				       has_table_privilege('app', :table, 'SELECT')   AS can_select,
				       has_table_privilege('app', :table, 'UPDATE')   AS can_update,
				       has_table_privilege('app', :table, 'DELETE')   AS can_delete,
				       has_table_privilege('app', :table, 'TRUNCATE') AS can_truncate
				""")
			.param("table", table)
			.query((rs, rowNum) -> new Privileges(rs.getBoolean("can_insert"), rs.getBoolean("can_select"),
					rs.getBoolean("can_update"), rs.getBoolean("can_delete"), rs.getBoolean("can_truncate")))
			.single();
	}

}
