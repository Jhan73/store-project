package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// Iterates real partitions (pg_inherits) instead of naming them, so a migration that adds a
// partition without repeating the revoke fails this test instead of silently weakening the guarantee.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class AuditAppendOnlyGrantsIT {

	record TablePrivileges(String tableName, boolean canInsert, boolean canSelect, boolean canUpdate,
			boolean canDelete, boolean canTruncate) {
	}

	@Autowired
	DataSource dataSource;

	@Test
	void appMayInsertAndSelectButNeverUpdateDeleteOrTruncateAnyPartition() {
		var tables = JdbcClient.create(dataSource).sql("""
				SELECT c.relname AS table_name,
				       has_table_privilege('app', c.oid, 'INSERT')   AS can_insert,
				       has_table_privilege('app', c.oid, 'SELECT')   AS can_select,
				       has_table_privilege('app', c.oid, 'UPDATE')   AS can_update,
				       has_table_privilege('app', c.oid, 'DELETE')   AS can_delete,
				       has_table_privilege('app', c.oid, 'TRUNCATE') AS can_truncate
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE n.nspname = 'audit'
				  AND (c.relname = 'audit_log'
				       OR c.oid IN (SELECT inhrelid FROM pg_inherits WHERE inhparent = 'audit.audit_log'::regclass))
				""")
			.query((rs, rowNum) -> new TablePrivileges(rs.getString("table_name"), rs.getBoolean("can_insert"),
					rs.getBoolean("can_select"), rs.getBoolean("can_update"), rs.getBoolean("can_delete"),
					rs.getBoolean("can_truncate")))
			.list();

		assertThat(tables).isNotEmpty();
		assertThat(tables).allSatisfy(table -> {
			assertThat(table.canInsert()).as("%s: INSERT", table.tableName()).isTrue();
			assertThat(table.canSelect()).as("%s: SELECT", table.tableName()).isTrue();
			assertThat(table.canUpdate()).as("%s: UPDATE", table.tableName()).isFalse();
			assertThat(table.canDelete()).as("%s: DELETE", table.tableName()).isFalse();
			assertThat(table.canTruncate()).as("%s: TRUNCATE", table.tableName()).isFalse();
		});
	}

}
