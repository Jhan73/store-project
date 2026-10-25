package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.RequestOrigin;
import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	@Mock
	AuditLogRepository auditLogs;

	@Mock
	CurrentActor currentActor;

	@Mock
	RequestOrigin requestOrigin;

	@Mock
	IdentityApi identity;

	@Mock
	AuditSearch search;

	AuditLogService service;

	@BeforeEach
	void setUp() {
		service = new AuditLogService(auditLogs, currentActor, requestOrigin, identity, search);
	}

	@Test
	void usesTheEventsOwnActorRoleWhenPresent() {
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(entry(Role.ADMIN));

		assertThat(saved().getActorRole()).isEqualTo("ADMIN");
	}

	@Test
	void resolvesAMissingActorToSystemOutsideARequest() {
		when(currentActor.isSystem()).thenReturn(true);
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(entry(null));

		assertThat(saved().getActorRole()).isEqualTo("SYSTEM");
	}

	@Test
	void resolvesAMissingActorToAnonymousDuringAnUnauthenticatedRequest() {
		when(currentActor.isSystem()).thenReturn(false);
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(entry(null));

		assertThat(saved().getActorRole()).isEqualTo("ANONYMOUS");
	}

	@Test
	void recordsTheClientIpAndUserAgentFromTheRequestOriginAbstraction() {
		when(currentActor.isSystem()).thenReturn(true);
		when(requestOrigin.clientIp()).thenReturn("203.0.113.7");
		when(requestOrigin.userAgent()).thenReturn("junit");
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(entry(null));

		assertThat(saved().getClientIp()).isEqualTo("203.0.113.7");
		assertThat(saved().getUserAgent()).isEqualTo("junit");
	}

	@Test
	void storesTheEntityLabelAndTheActorsUsernameAsTheActorLabel() {
		var actorId = UUID.randomUUID();
		when(identity.usernameOf(actorId)).thenReturn(Optional.of("Maria"));
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(new AuditEntry(NOW, actorId, Role.ADMIN, "PRODUCT_UPDATED", "PRODUCT", UUID.randomUUID(), null,
				null, null, "Mango juice"));

		assertThat(saved().getEntityLabel()).isEqualTo("Mango juice");
		assertThat(saved().getActorLabel()).isEqualTo("Maria");
		verify(identity, never()).emailOf(any());
	}

	@Test
	void leavesTheActorLabelEmptyWhenThereIsNoActingUser() {
		when(currentActor.isSystem()).thenReturn(true);
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(entry(null));

		assertThat(saved().getActorLabel()).isNull();
		assertThat(saved().getEntityLabel()).isNull();
	}

	@Test
	void leavesTheActorLabelEmptyWhenTheAccountHasNoUsername() {
		var actorId = UUID.randomUUID();
		when(identity.usernameOf(actorId)).thenReturn(Optional.empty());
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(new AuditEntry(NOW, actorId, Role.ADMIN, "USER_CREATED", "USER", UUID.randomUUID(), null, null,
				null, null));

		assertThat(saved().getActorLabel()).isNull();
	}

	@Test
	void clipsALabelToTheColumnLength() {
		when(currentActor.isSystem()).thenReturn(true);
		when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.record(new AuditEntry(NOW, null, null, "PRODUCT_CREATED", "PRODUCT", UUID.randomUUID(), null, null, null,
				"x".repeat(300)));

		assertThat(saved().getEntityLabel()).hasSize(AuditLogService.LABEL_MAX_LENGTH);
	}

	private AuditLog saved() {
		var captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(auditLogs).save(captor.capture());
		return captor.getValue();
	}

	private AuditEntry entry(Role actorRole) {
		var actorId = actorRole == null ? null : UUID.randomUUID();
		return new AuditEntry(NOW, actorId, actorRole, "USER_CREATED", "USER", UUID.randomUUID(), null, null, null, null);
	}

}
