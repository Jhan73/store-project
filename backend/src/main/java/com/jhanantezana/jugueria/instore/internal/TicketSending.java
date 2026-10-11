package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.TicketLinesSent;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;

/** Lines join the ticket's live card; once it is handed over or cancelled the next send opens a new round. */
@Service
public class TicketSending {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final PreparationApi preparation;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketSending(Tickets tickets, TicketService ticketService, PreparationApi preparation,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.preparation = preparation;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Nothing unsent is a no-op, so a retry of a send that already succeeded is harmless. */
	@Transactional
	public TicketView send(UUID ticketId) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		var lines = tickets.unsentForBoard(ticketId);
		if (lines.isEmpty()) {
			return ticketService.view(ticket);
		}
		var source = ticket.boardSourceId();
		if (source == null) {
			source = ticket.id();
			accept(ticket, source, 1, lines, now);
		}
		else if (!joinsCard(source, lines)) {
			source = Ids.newId();
			accept(ticket, source, ticket.boardRounds() + 1, lines, now);
		}
		var lineIds = lines.stream().map(BoardLineIntake::sourceLineId).toList();
		tickets.markSent(ticketId, lineIds, source, now);
		events.publishEvent(new TicketLinesSent(ticketId, source, lineIds, currentActor.id(), currentActor.role(), now));
		return ticketService.view(tickets.require(ticketId));
	}

	// A card the board no longer holds counts as closed: its lines were served long ago.
	private boolean joinsCard(UUID source, List<BoardLineIntake> lines) {
		return preparation.statusOf(BoardSource.TICKET, source).isPresent()
				&& preparation.tryAddLines(BoardSource.TICKET, source, lines);
	}

	private void accept(Tickets.TicketRow ticket, UUID source, int round, List<BoardLineIntake> lines, Instant now) {
		var atTable = ticket.type() == TicketType.TABLE;
		var label = atTable ? ticket.tableName() : ticket.counterName();
		preparation.accept(new BoardOrderIntake(BoardSource.TICKET, source, TicketRules.boardNumber(ticket.number(), round),
				atTable ? BoardChannel.TABLE : BoardChannel.COUNTER, label, now, null, lines));
		tickets.startRound(ticket.id(), source, round);
	}

}
