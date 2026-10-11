package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.instore.TicketTransferred;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

/** Where a ticket sits on the floor: moving it to another table and asking for the bill. */
@Service
public class TicketPlacement {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final PreparationApi preparation;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketPlacement(Tickets tickets, TicketService ticketService, PreparationApi preparation,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.preparation = preparation;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Moves a table ticket and relabels its cards in one transaction; moving it to its own table is a no-op. */
	@Transactional
	public TicketView transfer(UUID ticketId, UUID tableId) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		if (ticket.type() != TicketType.TABLE || ticket.tableId() == null) {
			throw new BusinessException(InstoreError.TICKET_HAS_NO_TABLE, "Ticket " + ticketId + " is not at a table");
		}
		if (tableId.equals(ticket.tableId())) {
			return ticketService.view(ticket);
		}
		var name = tickets.requireActiveTable(tableId);
		try {
			tickets.moveToTable(ticketId, tableId);
		}
		catch (DuplicateKeyException e) {
			throw new BusinessException(InstoreError.TABLE_OCCUPIED, "Table " + tableId + " already has an open ticket");
		}
		for (var source : tickets.boardSources(ticketId)) {
			preparation.relabel(BoardSource.TICKET, source, name);
		}
		events.publishEvent(new TicketTransferred(ticketId, ticket.tableId(), tableId, currentActor.id(),
				currentActor.role(), now));
		events.publishEvent(new TableOccupancyChanged(ticket.tableId(), now));
		events.publishEvent(new TableOccupancyChanged(tableId, now));
		return ticketService.view(tickets.require(ticketId));
	}

	/** Sets whether the table is waiting to pay; setting the value it already has changes nothing. */
	@Transactional
	public TicketView setBillRequested(UUID ticketId, boolean requested) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		if (tickets.setBillRequested(ticketId, requested) && ticket.tableId() != null) {
			events.publishEvent(new TableOccupancyChanged(ticket.tableId(), now));
		}
		return ticketService.view(tickets.require(ticketId));
	}

}
