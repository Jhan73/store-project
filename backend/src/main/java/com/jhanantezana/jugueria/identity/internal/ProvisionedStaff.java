package com.jhanantezana.jugueria.identity.internal;

import java.net.URI;
import java.time.Instant;

// Public: identity.web reads the created account from this record. setPasswordLink is exposed only to the
// bootstrap command's stdout; the REST API response never includes it.
public record ProvisionedStaff(UserAccount account, URI setPasswordLink, Instant linkExpiresAt) {
}
