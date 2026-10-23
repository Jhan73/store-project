package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.instore.TicketsMerged;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

/** Folds one open ticket into another; the lines keep their state and the cards they were sent to. */
@Service
public class TicketMerging {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final Payments payments;

	private final PreparationApi preparation;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketMerging(Tickets tickets, TicketService ticketService, Payments payments, PreparationApi preparation,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.payments = payments;
		this.preparation = preparation;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * Both tickets are locked in ascending id order, so two merges in opposite directions cannot wait on each other.
	 * Repeating a merge that already happened changes nothing. The source's cards stay on the board under the target's
	 * label.
	 */
	@Transactional
	public TicketView merge(UUID targetId, UUID sourceId) {
		if (targetId.equals(sourceId)) {
			throw new BusinessException(InstoreError.INVALID_MERGE, "A ticket cannot be merged into itself");
		}
		if (targetId.equals(tickets.mergedInto(sourceId).orElse(null))) {
			return ticketService.view(tickets.require(targetId));
		}
		var now = Instant.now(clock);
		var first = targetId.compareTo(sourceId) < 0 ? targetId : sourceId;
		var second = first.equals(targetId) ? sourceId : targetId;
		tickets.lockOpen(first, now);
		tickets.lockOpen(second, now);
		var target = tickets.require(targetId);
		var source = tickets.require(sourceId);
		if (payments.livePaid(sourceId).compareTo(BigDecimal.ZERO) != 0) {
			throw new BusinessException(InstoreError.TICKET_HAS_PAYMENTS,
					"Ticket " + sourceId + " has payments; void them before merging it");
		}
		if (tickets.liveLineCount(targetId) + tickets.liveLineCount(sourceId) > TicketLimits.MAX_LINES) {
			throw new BusinessException(InstoreError.TOO_MANY_LINES,
					"A ticket holds at most " + TicketLimits.MAX_LINES + " lines");
		}
		var cards = tickets.boardSources(sourceId);
		var moved = tickets.moveLines(sourceId, targetId, tickets.nextPosition(targetId));
		tickets.markMerged(sourceId, targetId, now);
		var label = target.type() == TicketType.TABLE ? target.tableName() : target.counterName();
		for (var card : cards) {
			preparation.relabel(BoardSource.TICKET, card, label);
		}
		events.publishEvent(new TicketsMerged(targetId, sourceId, source.number(), source.tableId(), moved,
				currentActor.id(), currentActor.role(), now));
		if (source.tableId() != null) {
			events.publishEvent(new TableOccupancyChanged(source.tableId(), now));
		}
		return ticketService.view(tickets.require(targetId));
	}

}
