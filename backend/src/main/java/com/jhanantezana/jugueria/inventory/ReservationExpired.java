package com.jhanantezana.jugueria.inventory;

import java.time.Instant;
import java.util.UUID;

/** A reference's reservation lapsed unpaid and its units went back on the shelf. */
public record ReservationExpired(UUID referenceId, Instant occurredAt) {
}
