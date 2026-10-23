package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.instore.TicketCancelled;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

/** Drops a ticket with nothing billed on it, so its table and its place in the open list are free again. */
@Service
public class TicketCancellation {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final Payments payments;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketCancellation(Tickets tickets, TicketService ticketService, Payments payments, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.payments = payments;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * Allowed while no line is billed (sent and not voided) and no payment is live; lines that never left the order
	 * screen are discarded. Cancelling a cancelled ticket changes nothing.
	 */
	@Transactional
	public TicketView cancel(UUID ticketId) {
		var existing = tickets.require(ticketId);
		if (existing.status() == TicketStatus.CANCELLED) {
			return ticketService.view(existing);
		}
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		var billed = tickets.lines(ticketId).stream().anyMatch(line -> line.state() == TicketLineState.SENT);
		if (billed || payments.livePaid(ticketId).signum() != 0) {
			throw new BusinessException(InstoreError.TICKET_NOT_EMPTY,
					"Ticket " + ticketId + " has billed lines or payments; void them first");
		}
		var discarded = tickets.lines(ticketId).stream().filter(line -> line.state() == TicketLineState.UNSENT).toList();
		tickets.deleteUnsentLines(ticketId);
		tickets.cancel(ticketId, now);
		events.publishEvent(new TicketCancelled(ticketId, ticket.number(), ticket.type(), ticket.tableId(),
				discarded.size(), discarded.stream().map(TicketLineView::productId).toList(), currentActor.id(), currentActor.role(), now));
		if (ticket.tableId() != null) {
			events.publishEvent(new TableOccupancyChanged(ticket.tableId(), now));
		}
		return ticketService.view(tickets.require(ticketId));
	}

}
