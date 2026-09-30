package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

// actorId/actorRole are null for the bootstrap command, which creates the first ADMIN with no acting user.
public record UserCreated(UUID userId, String email, Role role, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
