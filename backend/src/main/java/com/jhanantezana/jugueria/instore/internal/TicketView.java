package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Money;

public record TicketView(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
		@Nullable String counterName, @Nullable String note, TicketStatus status, boolean billRequested, Money total,
		Instant openedAt, List<TicketLineView> lines) {
}
