package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TicketLineVoided;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.ReasonType;

/** The ticket line and the board card change in one transaction, so neither keeps a line the other voided. */
@Service
public class TicketVoids {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final Payments payments;

	private final TicketSettlement settlement;

	private final PreparationApi preparation;

	private final InventoryApi inventory;

	private final VoidReasons voidReasons;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketVoids(Tickets tickets, TicketService ticketService, Payments payments, TicketSettlement settlement,
			PreparationApi preparation, InventoryApi inventory, VoidReasons voidReasons, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.payments = payments;
		this.settlement = settlement;
		this.preparation = preparation;
		this.inventory = inventory;
		this.voidReasons = voidReasons;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * A line that is already voided is a no-op, so a retry after the card closed still succeeds. A line a payment covers
	 * stays until the payment is voided, and so does any line whose void would leave the payments above what is owed. A
	 * void that leaves the ticket exactly paid closes it.
	 */
	@Transactional
	public TicketView voidLine(UUID ticketId, UUID lineId, String reasonCode, @Nullable String note) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		var line = tickets.requireLine(ticketId, lineId);
		if (line.state() == TicketLineState.VOIDED) {
			return ticketService.view(ticket);
		}
		if (line.state() != TicketLineState.SENT) {
			throw new BusinessException(InstoreError.LINE_NOT_SENT,
					"Line " + lineId + " was not sent; delete it instead of voiding it");
		}
		var text = TicketService.blankToNull(note);
		var reason = voidReasons.require(ReasonType.VOID, reasonCode, text);
		var role = currentActor.role();
		var actor = currentActor.id();
		if (role == null || actor == null) {
			throw new IllegalStateException("A void needs a signed-in user");
		}
		requireNotCovered(ticket, line);
		var waste = preparation.voidLine(BoardSource.TICKET, tickets.boardSourceOf(lineId), lineId,
				TicketRules.voidModeFor(role));
		tickets.markVoided(ticketId, lineId, waste, reason, text, actor, now);
		events.publishEvent(new TicketLineVoided(ticketId, lineId, line.productId(), line.quantity(), waste, reason, text,
				actor, role, now));
		if (waste) {
			inventory.recordWaste(lineId, List.of(new StockLine(line.productId(), line.quantity())));
		}
		settlement.closeWhenSettled(ticket, now);
		return ticketService.view(tickets.require(ticketId));
	}

	private void requireNotCovered(Tickets.TicketRow ticket, TicketLineView line) {
		if (payments.coveredLines(ticket.id()).contains(line.id())) {
			throw covered(line);
		}
		var remaining = TicketRules.total(tickets.lines(ticket.id()), ticket.currency()).minus(line.lineTotal());
		if (payments.livePaid(ticket.id()).compareTo(remaining.amount().max(BigDecimal.ZERO)) > 0) {
			throw covered(line);
		}
	}

	private static BusinessException covered(TicketLineView line) {
		return new BusinessException(InstoreError.LINE_COVERED_BY_PAYMENT,
				"Line " + line.id() + " is covered by a payment; void the payment first");
	}

}
