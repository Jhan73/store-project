package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.constraints.Size;

/** A ticket is for a table or for the counter's customer: exactly one of {@code tableId} and {@code counterName}. */
record OpenTicketRequest(@Nullable UUID tableId, @Nullable @Size(max = TicketLimits.MAX_COUNTER_NAME) String counterName,
		@Nullable @Size(max = TicketLimits.MAX_NOTE) String note) {
}
