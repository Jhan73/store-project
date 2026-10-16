package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code boardSourceId} is the card the lines went to; a later round of the same ticket has a different one. */
public record TicketLinesSent(UUID ticketId, UUID boardSourceId, List<UUID> lineIds, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
