package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// Deliberately reads the real system clock, not an injected one: this is a wall-clock canary that
// must start failing a full year before an uncovered year actually arrives, giving time to react.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class AuditPartitionCoverageIT {

	@Autowired
	DataSource dataSource;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void hasAYearlyPartitionCoveringNextYear() {
		var nextYear = LocalDate.now().getYear() + 1;
		var probeAt = OffsetDateTime.of(nextYear, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC);
		var id = UUID.randomUUID();
		var client = JdbcClient.create(dataSource);

		var partition = transactionTemplate.execute(status -> {
			client.sql("""
					INSERT INTO audit.audit_log (id, occurred_at, actor_id, actor_role, action, entity_type, entity_id)
					VALUES (:id, :occurredAt, NULL, 'SYSTEM', 'PARTITION_GUARD_PROBE', 'PARTITION_GUARD', :entityId)
					""")
				.param("id", id)
				.param("occurredAt", probeAt)
				.param("entityId", UUID.randomUUID())
				.update();
			var name = client.sql("SELECT tableoid::regclass::text FROM audit.audit_log WHERE id = :id")
				.param("id", id)
				.query(String.class)
				.single();
			// Never commits the probe row: audit_log is append-only, so this transaction is the only way to undo it.
			status.setRollbackOnly();
			return name;
		});

		assertThat(partition).as("no yearly partition covers %d; add one via a migration (see docs/runbooks/audit-partitions.md)",
				nextYear).isNotEqualTo("audit.audit_log_default");
	}

}
