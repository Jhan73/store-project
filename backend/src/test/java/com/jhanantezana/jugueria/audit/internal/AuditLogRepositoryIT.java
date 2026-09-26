package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// No @AfterEach cleanup: audit_log is append-only, so role app cannot delete its own test rows.
// Every assertion below is scoped to the row's own generated id, ignoring rows left by other tests.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class AuditLogRepositoryIT {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	DataSource dataSource;

	@Test
	void roundTripsBeforeAndAfterAsJson() {
		var entityId = UUID.randomUUID();
		var actorId = UUID.randomUUID();
		var saved = auditLogs.save(new AuditLog(NOW, actorId, "ADMIN", "USER_ROLE_CHANGED", "USER", entityId,
				Map.of("role", "CASHIER"), Map.of("role", "ADMIN"), null, "corr-1", "127.0.0.1", "junit"));

		var found = auditLogs.findById(saved.getId()).orElseThrow();

		assertThat(found.getBefore()).containsEntry("role", "CASHIER");
		assertThat(found.getAfter()).containsEntry("role", "ADMIN");
		assertThat(found.getActorId()).isEqualTo(actorId);
		assertThat(found.getEntityId()).isEqualTo(entityId);
	}

	@Test
	void insertAndSelectSucceedButUpdateAndDeleteAreDeniedForRoleApp() {
		var id = UUID.randomUUID();
		var client = JdbcClient.create(dataSource);

		client.sql("""
				INSERT INTO audit.audit_log
					(id, occurred_at, actor_id, actor_role, action, entity_type, entity_id)
				VALUES (:id, :occurredAt, NULL, 'SYSTEM', 'USER_CREATED', 'USER', :entityId)
				""")
			.param("id", id)
			.param("occurredAt", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
			.param("entityId", UUID.randomUUID())
			.update();

		var selected = client.sql("SELECT count(*) FROM audit.audit_log WHERE id = :id").param("id", id)
			.query(Integer.class)
			.single();
		assertThat(selected).isEqualTo(1);

		assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> client
			.sql("UPDATE audit.audit_log SET action = 'TAMPERED' WHERE id = :id")
			.param("id", id)
			.update());

		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> client.sql("DELETE FROM audit.audit_log WHERE id = :id").param("id", id).update());
	}

}
