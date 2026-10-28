package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.instore.SaleVoided;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.store.ReasonType;

/**
 * Voiding a recorded sale: its payments are reversed, the tracked stock it took goes back to the shelf, and the
 * record stays. The units are returned in the voiding transaction rather than by a listener on the event, because
 * {@code inventory} must not depend on {@code instore} while {@code instore} already calls it.
 */
@Service
public class TicketSaleVoids {

	private final Tickets tickets;

	private final TicketService ticketService;

	private final Payments payments;

	private final Shifts shifts;

	private final VoidReasons voidReasons;

	private final InventoryApi inventory;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketSaleVoids(Tickets tickets, TicketService ticketService, Payments payments, Shifts shifts,
			VoidReasons voidReasons, InventoryApi inventory, ApplicationEventPublisher events, CurrentActor currentActor,
			Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.payments = payments;
		this.shifts = shifts;
		this.voidReasons = voidReasons;
		this.inventory = inventory;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * A cashier voids only a sale paid in their own open shift; an ADMIN voids any. The cash goes back out of the
	 * voider's own open shift, or out of no drawer for an ADMIN without one. Voiding a voided sale changes nothing.
	 */
	@Transactional
	public TicketView voidSale(UUID ticketId, String reasonCode, @Nullable String note) {
		var now = Instant.now(clock);
		var actor = requireActor();
		var role = currentActor.role();
		var ticket = tickets.lockClosed(ticketId, now);
		if (payments.saleVoidOf(ticketId).isPresent()) {
			return ticketService.view(ticket);
		}
		var text = TicketService.blankToNull(note);
		var reason = voidReasons.require(ReasonType.VOID, reasonCode, text);
		var live = payments.live(ticketId);
		var shiftIds = new ArrayList<UUID>();
		for (var payment : live) {
			shiftIds.add(Voiding.shiftFor(shifts, actor, role, payment));
		}
		var saleVoidId = Ids.newId();
		if (!payments.insertSaleVoid(saleVoidId, ticketId, reason, text, actor, now)) {
			return ticketService.view(ticket);
		}
		for (var i = 0; i < live.size(); i++) {
			var payment = live.get(i);
			var shiftId = shiftIds.get(i);
			payments.insertVoid(Ids.newId(), payment.id(), ticketId, shiftId, saleVoidId, reason, text, actor, now);
			events.publishEvent(new PaymentVoided(payment.id(), ticketId, shiftId, payment.method(),
					payment.walletProviderName(), payment.paidMoney(),
					reason, text, saleVoidId, actor, role, now));
		}
		var lines = tickets.lines(ticketId);
		inventory.returnUnits(ticketId, TicketSettlement.stockLines(lines));
		events.publishEvent(new SaleVoided(ticketId, saleVoidId, ticket.type(), ticket.quickSale(),
				TicketRules.total(lines, ticket.currency()), TicketSettlement.soldLines(lines), reason, text, actor, role, now));
		return ticketService.view(tickets.require(ticketId));
	}

	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A void needs a signed-in user");
		}
		return id;
	}

}
