package com.jhanantezana.jugueria.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StockMovementAppendOnlyGrantsIT {

	record Privileges(boolean canInsert, boolean canSelect, boolean canUpdate, boolean canDelete,
			boolean canTruncate) {
	}

	@Autowired
	DataSource dataSource;

	@Test
	void appMayInsertAndSelectButNeverUpdateDeleteOrTruncateTheLedger() {
		var privileges = JdbcClient.create(dataSource).sql("""
				SELECT has_table_privilege('app', 'inventory.stock_movement', 'INSERT')   AS can_insert,
				       has_table_privilege('app', 'inventory.stock_movement', 'SELECT')   AS can_select,
				       has_table_privilege('app', 'inventory.stock_movement', 'UPDATE')   AS can_update,
				       has_table_privilege('app', 'inventory.stock_movement', 'DELETE')   AS can_delete,
				       has_table_privilege('app', 'inventory.stock_movement', 'TRUNCATE') AS can_truncate
				""")
			.query((rs, rowNum) -> new Privileges(rs.getBoolean("can_insert"), rs.getBoolean("can_select"),
					rs.getBoolean("can_update"), rs.getBoolean("can_delete"), rs.getBoolean("can_truncate")))
			.single();

		assertThat(privileges.canInsert()).as("INSERT").isTrue();
		assertThat(privileges.canSelect()).as("SELECT").isTrue();
		assertThat(privileges.canUpdate()).as("UPDATE").isFalse();
		assertThat(privileges.canDelete()).as("DELETE").isFalse();
		assertThat(privileges.canTruncate()).as("TRUNCATE").isFalse();
	}

}
