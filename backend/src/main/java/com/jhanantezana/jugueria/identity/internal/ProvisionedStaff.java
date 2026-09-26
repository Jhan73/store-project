package com.jhanantezana.jugueria.identity.internal;

import java.net.URI;
import java.time.Instant;

// setPasswordLink only reaches the bootstrap command's stdout, never the REST response.
public record ProvisionedStaff(UserAccount account, URI setPasswordLink, Instant linkExpiresAt) {
}
