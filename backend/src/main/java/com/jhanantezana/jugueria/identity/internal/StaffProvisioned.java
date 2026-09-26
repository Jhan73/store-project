package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;

// The raw token never leaves this module: identity.internal.StaffProvisioningService turns it into a link
// and hands it to notifications, without persisting or logging it.
record StaffProvisioned(UserAccount account, String rawSetPasswordToken, Instant setPasswordTokenExpiresAt) {
}
