package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

	AuditLogService service;

	@BeforeEach
	void setUp() {
		service = new AuditLogService(auditLogs, currentActor, requestOrigin);
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

	private AuditLog saved() {
		var captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(auditLogs).save(captor.capture());
		return captor.getValue();
	}

	private AuditEntry entry(Role actorRole) {
		var actorId = actorRole == null ? null : UUID.randomUUID();
		return new AuditEntry(NOW, actorId, actorRole, "USER_CREATED", "USER", UUID.randomUUID(), null, null, null);
	}

}
