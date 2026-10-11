package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.instore.internal.TicketStatus;
import com.jhanantezana.jugueria.instore.internal.TicketView;
import com.jhanantezana.jugueria.shared.Money;

record TicketResponse(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
		@Nullable String counterName, @Nullable String note, TicketStatus status, boolean billRequested, Money total,
		Instant openedAt, List<TicketLineResponse> lines) {

	static TicketResponse from(TicketView ticket) {
		return new TicketResponse(ticket.id(), ticket.number(), ticket.type(), ticket.tableId(), ticket.tableName(),
				ticket.counterName(), ticket.note(), ticket.status(), ticket.billRequested(), ticket.total(),
				ticket.openedAt(), ticket.lines().stream().map(TicketLineResponse::from).toList());
	}

}
