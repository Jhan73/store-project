package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** A ticket with nothing billed on it was dropped; its table is free again. */
public record TicketCancelled(UUID ticketId, long number, TicketType type, @Nullable UUID tableId,
		int discardedLines, List<UUID> discardedProductIds, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
