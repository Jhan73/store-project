package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.TableCreated;
import com.jhanantezana.jugueria.instore.TableSnapshot;
import com.jhanantezana.jugueria.instore.TableStatusChanged;
import com.jhanantezana.jugueria.instore.TableUpdated;
import com.jhanantezana.jugueria.shared.Role;

// One listener class per audited instore event; this file exercises all of them against real Postgres.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RecordAuditOnInstoreEventsIT {

	static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	TransactionTemplate transactionTemplate;

	@BeforeEach
	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void recordsATableCreation() {
		var tableId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new TableCreated(tableId, new TableSnapshot("Mesa 1", "Terrace", 2), actorId, Role.ADMIN, NOW));

		var entry = findByEntityId(tableId);
		assertThat(entry.getAction()).isEqualTo("TABLE_CREATED");
		assertThat(entry.getEntityType()).isEqualTo("TABLE");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getActorRole()).isEqualTo("ADMIN");
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("name", "Mesa 1")
			.containsEntry("area", "Terrace")
			.containsEntry("displayOrder", 2);
	}

	@Test
	void recordsATableUpdateWithBothSides() {
		var tableId = UUID.randomUUID();

		publish(new TableUpdated(tableId, new TableSnapshot("Mesa 1", "Terrace", 2), new TableSnapshot("Barra", null, 5),
				null, null, NOW));

		var entry = findByEntityId(tableId);
		assertThat(entry.getAction()).isEqualTo("TABLE_UPDATED");
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getBefore()).containsEntry("name", "Mesa 1").containsEntry("area", "Terrace");
		assertThat(entry.getAfter()).containsEntry("name", "Barra").containsEntry("area", null);
	}

	@Test
	void recordsATableStatusChange() {
		var tableId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new TableStatusChanged(tableId, false, actorId, Role.ADMIN, NOW));

		var entry = findByEntityId(tableId);
		assertThat(entry.getAction()).isEqualTo("TABLE_STATUS_CHANGED");
		assertThat(entry.getEntityType()).isEqualTo("TABLE");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getAfter()).containsEntry("active", false);
	}

	private void publish(Object event) {
		transactionTemplate.executeWithoutResult(status -> events.publishEvent(event));
	}

	private AuditLog findByEntityId(UUID entityId) {
		return auditLogs.findAll().stream().filter(entry -> entityId.equals(entry.getEntityId())).findFirst().orElseThrow();
	}

}
