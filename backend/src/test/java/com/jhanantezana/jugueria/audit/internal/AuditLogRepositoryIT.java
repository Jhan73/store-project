package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// No @AfterEach cleanup: audit_log is append-only, so role app cannot delete its own test rows.
// Every assertion below is scoped to the row's own generated id, ignoring rows left by other tests.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class AuditLogRepositoryIT {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	@Autowired
	AuditLogRepository auditLogs;

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

}
