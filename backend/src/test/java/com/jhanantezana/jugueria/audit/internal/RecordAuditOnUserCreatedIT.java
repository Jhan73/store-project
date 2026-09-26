package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.UserCreated;
import com.jhanantezana.jugueria.shared.Role;

// Publishes the identity module's public event directly, so this test only ever depends on that
// public surface (never on identity's internal/), matching "audit depends on identity's events only".
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RecordAuditOnUserCreatedIT {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void attributesAnEventWithNoActorToSystem() {
		var userId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new UserCreated(userId, "system-created@jugueria.pe", Role.ADMIN, null,
						null, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getActorId()).isNull();
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getAction()).isEqualTo("USER_CREATED");
		assertThat(entry.getAfter()).containsEntry("email", "system-created@jugueria.pe");
	}

	@Test
	void attributesAnEventWithAnActorToThatActor() {
		var userId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserCreated(userId, "admin-created@jugueria.pe", Role.CASHIER, actorId, Role.ADMIN, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getActorRole()).isEqualTo("ADMIN");
	}

	private AuditLog findByEntityId(UUID entityId) {
		return auditLogs.findAll()
			.stream()
			.filter(log -> log.getEntityId().equals(entityId))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No audit_log row for entity " + entityId));
	}

}
