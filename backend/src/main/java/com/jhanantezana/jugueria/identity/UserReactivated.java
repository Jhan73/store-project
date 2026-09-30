package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

public record UserReactivated(UUID userId, UUID actorId, Role actorRole, Instant occurredAt) {
}
