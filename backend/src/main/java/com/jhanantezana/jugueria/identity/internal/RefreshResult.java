package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

public record RefreshResult(String accessToken, String refreshToken, Instant refreshTokenExpiresAt, UUID userId,
		Role role) {
}
