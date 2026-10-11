package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code waste} is true for a line voided after it was ready. */
public record TicketLineVoided(UUID ticketId, UUID lineId, UUID productId, int quantity, boolean waste,
		String reasonCode, @Nullable String note, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
