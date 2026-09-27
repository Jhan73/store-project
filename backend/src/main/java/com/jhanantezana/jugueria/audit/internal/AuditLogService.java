package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.CorrelationId;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.RequestOrigin;
import com.jhanantezana.jugueria.shared.Role;

@Service
public class AuditLogService {

	// Reserved: never assignable to a real user account, only written here for system-initiated changes.
	static final String SYSTEM_ACTOR_ROLE = "SYSTEM";

	// A real request with nobody signed in, as opposed to no request at all (SYSTEM).
	static final String ANONYMOUS_ACTOR_ROLE = "ANONYMOUS";

	private final AuditLogRepository auditLogs;

	private final CurrentActor currentActor;

	private final RequestOrigin requestOrigin;

	AuditLogService(AuditLogRepository auditLogs, CurrentActor currentActor, RequestOrigin requestOrigin) {
		this.auditLogs = auditLogs;
		this.currentActor = currentActor;
		this.requestOrigin = requestOrigin;
	}

	// Runs in the caller's transaction (default propagation): a failure here rolls back the business change.
	@Transactional
	void record(AuditEntry entry) {
		auditLogs.save(new AuditLog(entry.occurredAt(), entry.actorId(), resolveActorRole(entry.actorRole()),
				entry.action(), entry.entityType(), entry.entityId(), entry.before(), entry.after(), entry.reason(),
				CorrelationId.current().orElse(null), requestOrigin.clientIp(), requestOrigin.userAgent()));
	}

	@Transactional(readOnly = true)
	public Page<AuditLog> search(@Nullable UUID actorId, @Nullable String entityType, @Nullable UUID entityId,
			@Nullable String action, @Nullable Instant from, @Nullable Instant to, Pageable pageable) {
		var filter = new AuditSearchFilter(actorId, entityType, entityId, action, from, to);
		return auditLogs.findAll(AuditLogSpecifications.matching(filter), pageable);
	}

	// The event runs on the publisher's own thread, so CurrentActor still reflects that same call.
	private String resolveActorRole(@Nullable Role actorRole) {
		if (actorRole != null) {
			return actorRole.name();
		}
		return currentActor.isSystem() ? SYSTEM_ACTOR_ROLE : ANONYMOUS_ACTOR_ROLE;
	}

}
