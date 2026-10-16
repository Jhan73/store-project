package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Money;

public record TicketSummary(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
		@Nullable String counterName, boolean billRequested, Money total, Instant openedAt) {
}
