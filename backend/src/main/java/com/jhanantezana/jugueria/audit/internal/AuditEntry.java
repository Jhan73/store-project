package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

// actorRole null means the change was system-initiated (no acting user), never an unauthenticated request.
record AuditEntry(Instant occurredAt, @Nullable UUID actorId, @Nullable Role actorRole, String action,
		String entityType, UUID entityId, @Nullable Map<String, Object> before, @Nullable Map<String, Object> after,
		@Nullable String reason) {
}
