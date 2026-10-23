package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.instore.internal.ClosedTicketSummary;
import com.jhanantezana.jugueria.shared.Money;

record ClosedTicketSummaryResponse(UUID id, long number, TicketType type, @Nullable UUID tableId,
		@Nullable String tableName, @Nullable String counterName, boolean quickSale, Money total, Instant closedAt,
		boolean voided, @Nullable UUID cashierId) {

	static ClosedTicketSummaryResponse from(ClosedTicketSummary ticket) {
		return new ClosedTicketSummaryResponse(ticket.id(), ticket.number(), ticket.type(), ticket.tableId(),
				ticket.tableName(), ticket.counterName(), ticket.quickSale(), ticket.total(), ticket.closedAt(),
				ticket.voided(), ticket.cashierId());
	}

}
