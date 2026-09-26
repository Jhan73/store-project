package com.jhanantezana.jugueria.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

// No @AfterEach cleanup: audit_log is append-only, so role app cannot delete its test rows. Every
// assertion below filters by a unique entityId generated per test, ignoring rows left by other tests.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditControllerIT {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	static final String AUDIT_ENTRIES = "/api/v1/audit-entries";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	AuditLogRepository auditLogs;

	@Test
	void rejectsWhenAnonymous() {
		var result = mvc.get().uri(AUDIT_ENTRIES).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsANonAdminRole() {
		var result = mvc.get().uri(AUDIT_ENTRIES).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void filtersByEntityIdAndSortsByOccurredAtDescending() {
		var entityId = UUID.randomUUID();
		var actorId = UUID.randomUUID();
		auditLogs.save(new AuditLog(NOW, actorId, "ADMIN", "USER_CREATED", "USER", entityId, null, null, null, null,
				null, null));
		auditLogs.save(new AuditLog(NOW.plusSeconds(60), actorId, "ADMIN", "USER_ROLE_CHANGED", "USER", entityId,
				null, null, null, null, null, null));

		var result = mvc.get()
			.uri(AUDIT_ENTRIES + "?entityId=" + entityId)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.content.length()").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.content[0].action").isEqualTo("USER_ROLE_CHANGED");
		assertThat(result).bodyJson().extractingPath("$.content[1].action").isEqualTo("USER_CREATED");
	}

	@Test
	void filtersByActorAndActionAndTimeRange() {
		var actorId = UUID.randomUUID();
		var entityId = UUID.randomUUID();
		auditLogs.save(new AuditLog(NOW, actorId, "ADMIN", "USER_DEACTIVATED", "USER", entityId, null, null, null,
				null, null, null));

		var result = mvc.get()
			.uri(AUDIT_ENTRIES + "?actorId=" + actorId + "&action=USER_DEACTIVATED&from=2026-09-26T00:00:00Z&to=2026-09-27T00:00:00Z")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.content[0].entityId").isEqualTo(entityId.toString());
	}

	@Test
	void excludesEntriesOutsideTheTimeRange() {
		var entityId = UUID.randomUUID();
		auditLogs.save(new AuditLog(NOW, null, "SYSTEM", "USER_CREATED", "USER", entityId, null, null, null, null,
				null, null));

		var result = mvc.get()
			.uri(AUDIT_ENTRIES + "?entityId=" + entityId + "&from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.content.length()").isEqualTo(0);
	}

	@Test
	void capsPageSizeAtOneHundred() {
		var result = mvc.get()
			.uri(AUDIT_ENTRIES + "?size=500")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(100);
	}

}
