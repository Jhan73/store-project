package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TicketLineVoided;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.StoreApi;

/** The ticket line and the board card change in one transaction, so neither keeps a line the other voided. */
@Service
public class TicketVoids {

	private static final String OTHER_REASON = "other";

	private final Tickets tickets;

	private final TicketService ticketService;

	private final PreparationApi preparation;

	private final StoreApi store;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketVoids(Tickets tickets, TicketService ticketService, PreparationApi preparation, StoreApi store,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.preparation = preparation;
		this.store = store;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** A line that is already voided is a no-op, so a retry after the card closed still succeeds. */
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
		var reason = requireReason(reasonCode, text);
		var role = currentActor.role();
		var actor = currentActor.id();
		if (role == null || actor == null) {
			throw new IllegalStateException("A void needs a signed-in user");
		}
		var waste = preparation.voidLine(BoardSource.TICKET, tickets.boardSourceOf(lineId), lineId,
				TicketRules.voidModeFor(role));
		tickets.markVoided(ticketId, lineId, waste, reason, text, actor, now);
		events.publishEvent(new TicketLineVoided(ticketId, lineId, line.productId(), line.quantity(), waste, reason, text,
				actor, role, now));
		return ticketService.view(ticket);
	}

	private String requireReason(String reasonCode, @Nullable String note) {
		var reason = store.reasons(ReasonType.VOID)
			.stream()
			.filter(candidate -> candidate.active() && candidate.code().equalsIgnoreCase(reasonCode.trim()))
			.findFirst()
			.orElseThrow(() -> new BusinessException(InstoreError.INVALID_REASON,
					"The reason is not an active void reason"));
		if (OTHER_REASON.equalsIgnoreCase(reason.code()) && note == null) {
			throw new BusinessException(InstoreError.INVALID_REASON, "The reason \"Other\" needs a note");
		}
		return reason.code();
	}

}
