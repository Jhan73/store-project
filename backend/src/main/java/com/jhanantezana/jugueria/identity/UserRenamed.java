package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

// oldUsername is null when the account had none yet.
public record UserRenamed(UUID userId, @Nullable String oldUsername, String newUsername, UUID actorId,
		Role actorRole, Instant occurredAt) {
}
