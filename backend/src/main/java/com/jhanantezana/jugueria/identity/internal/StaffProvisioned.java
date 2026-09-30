package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;

// The raw token stays in this module; only the link built from it leaves, via notifications.
record StaffProvisioned(UserAccount account, String rawSetPasswordToken, Instant setPasswordTokenExpiresAt) {
}
