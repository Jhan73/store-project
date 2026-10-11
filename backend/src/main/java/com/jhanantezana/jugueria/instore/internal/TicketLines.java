package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;

/** Adding, changing, deleting and repeating the lines a ticket has not sent yet. */
@Service
public class TicketLines {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final CatalogApi catalog;

	private final Clock clock;

	TicketLines(Tickets tickets, TicketService ticketService, CatalogApi catalog, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.catalog = catalog;
		this.clock = clock;
	}

	@Transactional
	public TicketView add(UUID ticketId, LineDraft draft) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		tickets.insertLine(ticketId, tickets.nextPosition(ticketId), price(draft, ticket.currency()), draft, now);
		return ticketService.view(ticket);
	}

	@Transactional
	public TicketView change(UUID ticketId, UUID lineId, LineDraft draft) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		tickets.requireLine(ticketId, lineId);
		if (!tickets.replaceUnsentLine(ticketId, lineId, price(draft, ticket.currency()), draft, now)) {
			throw alreadySent(lineId);
		}
		return ticketService.view(ticket);
	}

	@Transactional
	public void delete(UUID ticketId, UUID lineId) {
		tickets.lockOpen(ticketId, Instant.now(clock));
		tickets.requireLine(ticketId, lineId);
		if (!tickets.deleteUnsentLine(ticketId, lineId)) {
			throw alreadySent(lineId);
		}
	}

	/** Copies lines as new unsent ones, priced and validated again; either every line is copied or none. */
	@Transactional
	public TicketView repeat(UUID ticketId, List<UUID> lineIds) {
		var now = Instant.now(clock);
		var ticket = tickets.lockOpen(ticketId, now);
		var position = tickets.nextPosition(ticketId);
		for (var lineId : lineIds) {
			var source = tickets.requireLine(ticketId, lineId);
			var draft = new LineDraft(source.productId(),
					source.modifiers().stream().map(ChosenOption::optionId).toList(), source.quantity(), source.note(),
					source.allergy());
			tickets.insertLine(ticketId, position++, price(draft, ticket.currency()), draft, now);
		}
		return ticketService.view(ticket);
	}

	private LinePrice price(LineDraft draft, Currency ticketCurrency) {
		var priced = catalog.priceSelection(draft.productId(), draft.optionIds());
		if (!priced.unitPrice().currency().equals(ticketCurrency)) {
			throw new BusinessException(CatalogError.PRICE_CURRENCY_MISMATCH,
					"The product is priced in another currency than the ticket");
		}
		var options = priced.options().stream().map(o -> new ChosenOption(o.optionId(), o.name(), o.priceDelta())).toList();
		return new LinePrice(priced.productId(), priced.productName(), priced.stationId(), priced.unitPrice(), options);
	}

	private static BusinessException alreadySent(UUID lineId) {
		return new BusinessException(InstoreError.LINE_ALREADY_SENT,
				"Line " + lineId + " was already sent; void it and add a new one");
	}

}
