package com.jhanantezana.jugueria.identity.internal;

import java.time.Duration;
import java.time.Instant;

public record IssuedRefreshToken(String rawToken, Instant expiresAt, Duration maxAge) {
}
