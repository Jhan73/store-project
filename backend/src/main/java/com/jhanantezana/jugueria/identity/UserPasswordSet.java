package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

// The account itself is the actor: a set-password token is proof of identity, never a stored secret.
public record UserPasswordSet(UUID userId, Role role, Instant occurredAt) {
}
