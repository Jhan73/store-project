package com.jhanantezana.jugueria.identity.internal;

import java.time.Duration;
import java.time.Instant;

// sessionExpiresAt is when the whole token family ends, however often it is rotated.
public record IssuedRefreshToken(String rawToken, Instant expiresAt, Duration maxAge, Instant sessionExpiresAt) {
}
