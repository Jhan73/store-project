package com.jhanantezana.jugueria.instore.internal;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;

/** Every field narrows the search; days are store days and both ends are included. */
public record ClosedTicketFilter(@Nullable LocalDate from, @Nullable LocalDate to, @Nullable UUID cashierId,
		@Nullable UUID tableId, @Nullable TicketType type) {
}
