package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

// actorRole null means the change was system-initiated (no acting user), never an unauthenticated request.
// entityLabel is what a person calls the entity (a name, an email, a ticket number), null when it has none.
record AuditEntry(Instant occurredAt, @Nullable UUID actorId, @Nullable Role actorRole, String action,
		String entityType, UUID entityId, @Nullable Map<String, Object> before, @Nullable Map<String, Object> after,
		@Nullable String reason, @Nullable String entityLabel) {

	// The entity is gone afterwards, so the row alone must say what it was: the whole snapshot goes in before.
	static AuditEntry deletion(Instant occurredAt, @Nullable UUID actorId, @Nullable Role actorRole, String entityType,
			UUID entityId, Map<String, Object> snapshot, @Nullable String entityLabel, @Nullable String reason) {
		return new AuditEntry(occurredAt, actorId, actorRole, entityType + "_DELETED", entityType, entityId, snapshot,
				null, reason, entityLabel);
	}

}
