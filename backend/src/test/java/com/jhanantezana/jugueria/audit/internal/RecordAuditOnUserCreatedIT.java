package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.UserCreated;
import com.jhanantezana.jugueria.identity.UserDeactivated;
import com.jhanantezana.jugueria.identity.UserDeleted;
import com.jhanantezana.jugueria.identity.UserRenamed;
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

	@Autowired
	JdbcClient jdbc;

	// Guards both directions: a leftover request context from another test must never leak in or out.
	@BeforeEach
	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void attributesAnEventWithNoActorToSystem() {
		var userId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new UserCreated(userId, "system-created@jugueria.pe", "Owner",
						Role.ADMIN, null, null, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getActorId()).isNull();
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getAction()).isEqualTo("USER_CREATED");
		assertThat(entry.getAfter()).containsEntry("username", "Owner").containsEntry("role", "ADMIN");
		assertThat(entry.getAfter()).doesNotContainKey("email");
		assertThat(entry.getEntityLabel()).isEqualTo("Owner");
		assertThat(entry.getActorLabel()).isNull();
	}

	@Test
	void recordsADeactivationOfAnAccountThatCannotBeNamedWithoutInventingALabel() {
		var userId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserDeactivated(userId, UUID.randomUUID(), Role.ADMIN, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getAction()).isEqualTo("USER_DEACTIVATED");
		assertThat(entry.getBefore()).containsEntry("active", true);
		assertThat(entry.getAfter()).containsEntry("active", false).doesNotContainKey("email");
		assertThat(entry.getEntityLabel()).isNull();
	}

	@Test
	void recordsAnAccountDeletionWithItsOwnSnapshotBecauseTheAccountIsGone() {
		var userId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserDeleted(userId, "Maria", Role.CASHIER, true, actorId, Role.ADMIN, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getAction()).isEqualTo("USER_DELETED");
		assertThat(entry.getEntityLabel()).isEqualTo("Maria");
		assertThat(entry.getAfter()).isNull();
		assertThat(entry.getBefore()).containsEntry("username", "Maria")
			.containsEntry("role", "CASHIER")
			.containsEntry("active", true)
			.doesNotContainKey("email");
	}

	@Test
	void attributesAnEventWithNoActorToAnonymousDuringARequest() {
		var userId = UUID.randomUUID();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new UserCreated(userId, "self-registered@jugueria.pe", null,
						Role.CUSTOMER, null, null, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getActorId()).isNull();
		assertThat(entry.getActorRole()).isEqualTo("ANONYMOUS");
	}

	@Test
	void attributesAnEventWithAnActorToThatActor() {
		var userId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserCreated(userId, "admin-created@jugueria.pe", "Maria", Role.CASHIER, actorId, Role.ADMIN, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getActorRole()).isEqualTo("ADMIN");
	}

	@Test
	void leavesTheLabelEmptyForAnAccountCreatedWithoutAUsername() {
		var userId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserCreated(userId, "owner@jugueria.pe", null, Role.ADMIN, null, null, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getEntityLabel()).isNull();
		assertThat(entry.getAfter()).doesNotContainKey("username").doesNotContainKey("email");
	}

	@Test
	void recordsARenameWithTheOldAndNewUsernameAndNeverAnEmail() {
		var userId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserRenamed(userId, "Mari", "Maria", UUID.randomUUID(), Role.ADMIN, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getAction()).isEqualTo("USER_RENAMED");
		assertThat(entry.getEntityType()).isEqualTo("USER");
		assertThat(entry.getBefore()).containsExactly(Map.entry("username", "Mari"));
		assertThat(entry.getAfter()).containsExactly(Map.entry("username", "Maria"));
		assertThat(entry.getEntityLabel()).isEqualTo("Maria");
		assertThat(entry.getActorRole()).isEqualTo("ADMIN");
	}

	@Test
	void recordsTheFirstUsernameOfALegacyAccountWithoutABeforeValue() {
		var userId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new UserRenamed(userId, null, "Owner", UUID.randomUUID(), Role.ADMIN, NOW)));

		var entry = findByEntityId(userId);
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("username", "Owner");
	}

	@Test
	void labelsAUserAndItsActorByUsernameNeverByEmail() {
		var actorId = UUID.randomUUID();
		var userId = UUID.randomUUID();
		insertAccount(actorId, "admin@jugueria.pe", "Ana", "ADMIN");
		insertAccount(userId, "maria@jugueria.pe", "Maria", "CASHIER");
		try {
			transactionTemplate.executeWithoutResult(
					status -> events.publishEvent(new UserDeactivated(userId, actorId, Role.ADMIN, NOW)));

			var entry = findByEntityId(userId);
			assertThat(entry.getEntityLabel()).isEqualTo("Maria");
			assertThat(entry.getActorLabel()).isEqualTo("Ana");
			assertThat(entry.getActorRole()).isEqualTo("ADMIN");
			assertThat(entry.getBefore()).containsEntry("username", "Maria").doesNotContainKey("email");
			assertThat(entry.getAfter()).containsEntry("username", "Maria").doesNotContainKey("email");
		}
		finally {
			jdbc.sql("delete from identity.user_account where id in (?, ?)").params(actorId, userId).update();
		}
	}

	@Test
	void leavesTheLabelsEmptyForALegacyAccountWithoutAUsername() {
		var actorId = UUID.randomUUID();
		insertAccount(actorId, "legacy@jugueria.pe", null, "ADMIN");
		try {
			var userId = actorId;
			transactionTemplate.executeWithoutResult(
					status -> events.publishEvent(new UserDeactivated(userId, actorId, Role.ADMIN, NOW)));

			var entry = findByEntityId(userId);
			assertThat(entry.getEntityLabel()).isNull();
			assertThat(entry.getActorLabel()).isNull();
			assertThat(entry.getActorRole()).isEqualTo("ADMIN");
		}
		finally {
			jdbc.sql("delete from identity.user_account where id = ?").param(actorId).update();
		}
	}

	private void insertAccount(UUID id, String email, String username, String role) {
		jdbc.sql("""
				insert into identity.user_account (id, email, username, password_hash, role, created_at, updated_at)
				values (?, ?, ?, 'hash', ?, now(), now())
				""").params(id, email, username, role).update();
	}

	private AuditLog findByEntityId(UUID entityId) {
		return auditLogs.findAll()
			.stream()
			.filter(log -> log.getEntityId().equals(entityId))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No audit_log row for entity " + entityId));
	}

}
