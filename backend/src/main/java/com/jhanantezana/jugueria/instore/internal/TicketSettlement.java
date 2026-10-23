package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.SaleRecorded;
import com.jhanantezana.jugueria.instore.SoldLine;
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Money;

/**
 * Closes a ticket once its payments cover the total. Stock is deducted here and nowhere earlier, in the closing
 * transaction; money already taken is never rejected, so a sale the shelf cannot cover takes stock below zero and
 * raises a stock deficit.
 */
@Component
class TicketSettlement {

	private final Tickets tickets;

	private final Payments payments;

	private final InventoryApi inventory;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	TicketSettlement(Tickets tickets, Payments payments, InventoryApi inventory, ApplicationEventPublisher events, CurrentActor currentActor) {
		this.tickets = tickets;
		this.payments = payments;
		this.inventory = inventory;
		this.events = events;
		this.currentActor = currentActor;
	}

	/** The caller holds the ticket's lock. @return whether the ticket was closed */
	boolean closeWhenSettled(Tickets.TicketRow ticket, Instant now) {
		var lines = tickets.lines(ticket.id());
		if (lines.stream().anyMatch(line -> line.state() == TicketLineState.UNSENT)) {
			return false;
		}
		var total = TicketRules.total(lines, ticket.currency());
		var paid = new Money(payments.livePaid(ticket.id()), ticket.currency());
		if (total.isZero() || paid.compareTo(total) != 0) {
			return false;
		}
		inventory.deductAllowingShortfall(ticket.id(), stockLines(lines));
		tickets.close(ticket.id(), now);
		// A void can close the ticket, and whoever voided it may be a server with no shift: the sale is the cashier's.
		var seller = payments.lastLivePayer(ticket.id()).orElseGet(currentActor::id);
		var sellerRole = seller != null && seller.equals(currentActor.id()) ? currentActor.role() : null;
		events.publishEvent(new SaleRecorded(ticket.id(), ticket.number(), ticket.type(), ticket.quickSale(), total,
				soldLines(lines), seller, sellerRole, now));
		if (ticket.tableId() != null) {
			events.publishEvent(new TableOccupancyChanged(ticket.tableId(), now));
		}
		return true;
	}

	/** The charged lines: what the sale took off the shelf and what a void of it puts back. */
	static List<StockLine> stockLines(List<TicketLineView> lines) {
		return lines.stream()
			.filter(line -> line.state() == TicketLineState.SENT)
			.map(line -> new StockLine(line.productId(), line.quantity()))
			.toList();
	}

	static List<SoldLine> soldLines(List<TicketLineView> lines) {
		return lines.stream()
			.filter(line -> line.state() == TicketLineState.SENT)
			.map(line -> new SoldLine(line.id(), line.productId(), line.productName(), line.quantity(), line.unitPrice(),
					line.lineTotal(), line.modifiers().stream().map(ChosenOption::name).toList()))
			.toList();
	}

}
