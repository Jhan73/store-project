package com.jhanantezana.jugueria.store;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record ReasonStatusChanged(UUID reasonId, boolean active, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
