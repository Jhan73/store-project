package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

public record UserRoleChanged(UUID userId, Role oldRole, Role newRole, UUID actorId, Role actorRole,
		Instant occurredAt) {
}
