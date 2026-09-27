package com.jhanantezana.jugueria.store;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record ReasonCreated(UUID reasonId, ReasonType type, String code, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
