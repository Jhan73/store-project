package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.instore.internal.TicketSummary;
import com.jhanantezana.jugueria.shared.Money;

record TicketSummaryResponse(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
		@Nullable String counterName, boolean billRequested, Money total, Instant openedAt) {

	static TicketSummaryResponse from(TicketSummary ticket) {
		return new TicketSummaryResponse(ticket.id(), ticket.number(), ticket.type(), ticket.tableId(),
				ticket.tableName(), ticket.counterName(), ticket.billRequested(), ticket.total(), ticket.openedAt());
	}

}
