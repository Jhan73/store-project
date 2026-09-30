package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

record AuditSearchFilter(@Nullable UUID actorId, @Nullable String entityType, @Nullable UUID entityId,
		@Nullable String action, @Nullable Instant from, @Nullable Instant to) {
}
