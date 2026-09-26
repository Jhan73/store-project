package com.jhanantezana.jugueria.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

public record LoginResult(String accessToken, String refreshToken, Instant refreshTokenExpiresAt,
		Duration refreshTokenMaxAge, UUID userId, Role role) {
}
