package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record TicketTransferred(UUID ticketId, UUID fromTableId, UUID toTableId, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
