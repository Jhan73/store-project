package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** The account is gone, so the event carries what the audit row needs; never an email address. */
public record UserDeleted(UUID userId, @Nullable String username, Role role, boolean active, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
