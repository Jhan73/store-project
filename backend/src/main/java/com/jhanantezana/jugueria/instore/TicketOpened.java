package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record TicketOpened(UUID ticketId, long number, TicketType type, @Nullable UUID tableId,
		@Nullable String counterName, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
