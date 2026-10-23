package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TicketLineComped;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.ReasonType;

/** Serving a sent line free of charge: the ticket line and the board card change in one transaction. */
@Service
public class TicketComps {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final PaidLineGuard paidLines;

	private final TicketSettlement settlement;

	private final PreparationApi preparation;

	private final InventoryApi inventory;

	private final VoidReasons reasons;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketComps(Tickets tickets, TicketService ticketService, PaidLineGuard paidLines, TicketSettlement settlement,
			PreparationApi preparation, InventoryApi inventory, VoidReasons reasons, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.paidLines = paidLines;
		this.settlement = settlement;
		this.preparation = preparation;
		this.inventory = inventory;
		this.reasons = reasons;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * A line that is already comped is a no-op, so a retry still succeeds. A line a payment covers stays until the
	 * payment is voided, and so does any line whose comp would leave the payments above what is owed. A comp that leaves
	 * the ticket exactly paid closes it. The units leave the shelf as waste, once per line.
	 */
	@Transactional
	public TicketView comp(UUID ticketId, UUID lineId, String reasonCode, @Nullable String note) {
		var now = Instant.now(clock);
		Tickets.TicketRow ticket;
		try {
			ticket = tickets.lockOpen(ticketId, now);
		}
		catch (BusinessException e) {
			// An overlapping comp of this same line may have just closed the ticket.
			if (e.errorCode() == InstoreError.TICKET_CLOSED
					&& tickets.line(ticketId, lineId).filter(l -> l.state() == TicketLineState.COMPED).isPresent()) {
				return ticketService.view(tickets.require(ticketId));
			}
			throw e;
		}
		var line = tickets.requireLine(ticketId, lineId);
		if (line.state() == TicketLineState.COMPED) {
			return ticketService.view(ticket);
		}
		if (line.state() == TicketLineState.VOIDED) {
			throw new BusinessException(InstoreError.LINE_VOIDED, "Line " + lineId + " was voided; it cannot be comped");
		}
		if (line.state() != TicketLineState.SENT) {
			throw new BusinessException(InstoreError.LINE_NOT_SENT, "Line " + lineId + " was not sent; delete it instead");
		}
		var text = TicketService.blankToNull(note);
		var reason = reasons.require(ReasonType.COMP, reasonCode, text);
		var role = currentActor.role();
		var actor = currentActor.id();
		if (role == null || actor == null) {
			throw new IllegalStateException("A comp needs a signed-in user");
		}
		paidLines.requireNotCovered(ticket, line);
		preparation.compLine(BoardSource.TICKET, tickets.boardSourceOf(lineId), lineId);
		tickets.markComped(ticketId, lineId, reason, text, actor, now);
		inventory.recordWaste(lineId, List.of(new StockLine(line.productId(), line.quantity())));
		var closed = settlement.isSettled(ticket);
		events.publishEvent(new TicketLineComped(ticketId, lineId, line.productId(), line.quantity(), reason, text, actor,
				role, now, line.lineTotal(), closed, ticket.type(), ticket.quickSale(), line.productName(), line.unitPrice(),
				line.modifiers().stream().map(ChosenOption::name).toList()));
		if (closed) {
			settlement.close(ticket, now);
		}
		return ticketService.view(tickets.require(ticketId));
	}

}
