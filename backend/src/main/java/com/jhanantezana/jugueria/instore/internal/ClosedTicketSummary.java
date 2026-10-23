package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Money;

/** {@code cashierId} is who recorded the last payment; {@code voided} says the sale was voided afterwards. */
public record ClosedTicketSummary(UUID id, long number, TicketType type, @Nullable UUID tableId,
		@Nullable String tableName, @Nullable String counterName, boolean quickSale, Money total, Instant closedAt,
		boolean voided, @Nullable UUID cashierId) {
}
