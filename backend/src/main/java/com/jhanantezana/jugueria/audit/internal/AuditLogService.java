package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.IdentityApi;
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

	// Matches the entity_label and actor_label columns.
	static final int LABEL_MAX_LENGTH = 120;

	private final RequestOrigin requestOrigin;

	private final IdentityApi identity;

	private final AuditSearch search;

	AuditLogService(AuditLogRepository auditLogs, CurrentActor currentActor, RequestOrigin requestOrigin,
			IdentityApi identity, AuditSearch search) {
		this.auditLogs = auditLogs;
		this.currentActor = currentActor;
		this.requestOrigin = requestOrigin;
		this.identity = identity;
		this.search = search;
	}

	// Runs in the caller's transaction (default propagation): a failure here rolls back the business change.
	@Transactional
	void record(AuditEntry entry) {
		auditLogs.save(new AuditLog(entry.occurredAt(), entry.actorId(), resolveActorRole(entry.actorRole()),
				entry.action(), entry.entityType(), entry.entityId(), entry.before(), entry.after(), entry.reason(),
				CorrelationId.current().orElse(null), requestOrigin.clientIp(), requestOrigin.userAgent(),
				clip(entry.entityLabel()), clip(actorLabel(entry.actorId()))));
	}

	@Transactional(readOnly = true)
	public Page<AuditLog> search(@Nullable UUID actorId, @Nullable String entityType, @Nullable UUID entityId,
			@Nullable String action, @Nullable Instant from, @Nullable Instant to, Pageable pageable) {
		return search.search(new AuditSearchFilter(actorId, entityType, entityId, action, from, to), pageable);
	}

	// Read while the account still exists: the same transaction as the change, so a later deletion cannot lose it.
	private @Nullable String actorLabel(@Nullable UUID actorId) {
		return actorId == null ? null : identity.emailOf(actorId).orElse(null);
	}

	private static @Nullable String clip(@Nullable String label) {
		if (label == null || label.isBlank()) {
			return null;
		}
		return label.length() <= LABEL_MAX_LENGTH ? label : label.substring(0, LABEL_MAX_LENGTH);
	}

	// The event runs on the publisher's own thread, so CurrentActor still reflects that same call.
	private String resolveActorRole(@Nullable Role actorRole) {
		if (actorRole != null) {
			return actorRole.name();
		}
		return currentActor.isSystem() ? SYSTEM_ACTOR_ROLE : ANONYMOUS_ACTOR_ROLE;
	}

}
