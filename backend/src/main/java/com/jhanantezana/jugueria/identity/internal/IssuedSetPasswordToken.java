package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;

record IssuedSetPasswordToken(String rawToken, Instant expiresAt) {
}
