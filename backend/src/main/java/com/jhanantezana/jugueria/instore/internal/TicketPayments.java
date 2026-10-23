package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.PaymentRecorded;
import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.ReasonType;

/**
 * Recording and voiding the payments of an open ticket. Every command starts with the ticket's lock and then shares
 * the cashier's shift, so a shift cannot close between the check and the payment and a ticket is never paid twice
 * beyond its total.
 */
@Service
public class TicketPayments {

	static final int MIN_SPLIT_PARTS = 2;

	static final int MAX_SPLIT_PARTS = 20;

	private final Tickets tickets;

	private final TicketService ticketService;

	private final Payments payments;

	private final Shifts shifts;

	private final TicketSettlement settlement;

	private final VoidReasons voidReasons;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketPayments(Tickets tickets, TicketService ticketService, Payments payments, Shifts shifts,
			TicketSettlement settlement, VoidReasons voidReasons, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.tickets = tickets;
		this.ticketService = ticketService;
		this.payments = payments;
		this.shifts = shifts;
		this.settlement = settlement;
		this.voidReasons = voidReasons;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * Takes a payment into the cashier's open shift. Cash states the amount received and the change follows; every
	 * other method states none. A payment by items must equal the chosen lines. The payment that covers the total
	 * closes the ticket and deducts its stock, below zero if the shelf cannot cover the sale.
	 */
	@Transactional
	public TicketView record(UUID ticketId, PaymentMethod method, Money amount, @Nullable Money received,
			@Nullable List<UUID> lineIds) {
		var now = Instant.now(clock);
		var actor = requireActor();
		var ticket = tickets.lockOpen(ticketId, now);
		var shift = shifts.requireWritableOf(actor);
		var lines = tickets.lines(ticketId);
		if (lines.stream().anyMatch(line -> line.state() == TicketLineState.UNSENT)) {
			throw new BusinessException(InstoreError.TICKET_HAS_UNSENT_LINES,
					"Send or delete the unsent lines before taking payment");
		}
		var currency = ticket.currency();
		Amounts.require(amount, currency, false, "The payment");
		var total = TicketRules.total(lines, currency);
		var balance = total.minus(new Money(payments.livePaid(ticketId), currency));
		if (amount.compareTo(balance) > 0) {
			throw new BusinessException(InstoreError.PAYMENT_EXCEEDS_BALANCE,
					"The payment is more than what is still owed", Map.of("balance", balance));
		}
		var change = change(method, amount, received, currency);
		var items = lineIds == null ? List.<UUID>of() : List.copyOf(new HashSet<>(lineIds));
		if (!items.isEmpty()) {
			requireItemsPayment(ticketId, lines, items, amount);
		}
		var id = Ids.newId();
		payments.insert(id, ticketId, shift.id(), method, amount, received, change, items, actor, now);
		var closed = settlement.closeWhenSettled(ticket, now);
		events.publishEvent(new PaymentRecorded(id, ticketId, shift.id(), method, amount, received,
				method == PaymentMethod.CASH ? change : null, items, closed, actor, currentActor.role(), now));
		return ticketService.view(tickets.require(ticketId));
	}

	/**
	 * Reverses a payment of an open ticket, which frees its lines again. The cash goes back out of the voider's own
	 * open shift; an ADMIN without one voids it with no drawer behind it. Voiding a voided payment changes nothing.
	 */
	@Transactional
	public TicketView voidPayment(UUID ticketId, UUID paymentId, String reasonCode, @Nullable String note) {
		var now = Instant.now(clock);
		var actor = requireActor();
		var ticket = tickets.lockOpen(ticketId, now);
		var payment = payments.find(ticketId, paymentId)
			.orElseThrow(() -> new BusinessException(InstoreError.PAYMENT_NOT_FOUND, "Payment not found"));
		if (payment.voided()) {
			return ticketService.view(ticket);
		}
		var text = TicketService.blankToNull(note);
		var reason = voidReasons.require(ReasonType.VOID, reasonCode, text);
		var shiftId = Voiding.shiftFor(shifts, actor, currentActor.role(), payment);
		if (payments.insertVoid(Ids.newId(), paymentId, ticketId, shiftId, null, reason, text, actor, now)) {
			events.publishEvent(new PaymentVoided(paymentId, ticketId, shiftId, payment.method(), payment.paidMoney(),
					reason, text, null, actor, currentActor.role(), now));
		}
		return ticketService.view(tickets.require(ticketId));
	}

	/** The amounts of an even split of what is still owed: all equal rounded down to cents, the last takes the rest. */
	@Transactional(readOnly = true)
	public List<Money> split(UUID ticketId, int parts) {
		var ticket = tickets.require(ticketId);
		if (ticket.status() != TicketStatus.OPEN) {
			throw new BusinessException(InstoreError.TICKET_CLOSED, "Ticket " + ticketId + " is not open");
		}
		if (parts < MIN_SPLIT_PARTS || parts > MAX_SPLIT_PARTS) {
			throw new BusinessException(InstoreError.INVALID_PAYMENT,
					"A bill is split in " + MIN_SPLIT_PARTS + " to " + MAX_SPLIT_PARTS + " parts");
		}
		var total = TicketRules.total(tickets.lines(ticketId), ticket.currency());
		var balance = total.minus(new Money(payments.livePaid(ticketId), ticket.currency()));
		try {
			return BillSplit.evenly(balance, parts);
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(InstoreError.INVALID_PAYMENT, "There is too little owed to split in " + parts + " parts");
		}
	}

	private Money change(PaymentMethod method, Money amount, @Nullable Money received, java.util.Currency currency) {
		if (method != PaymentMethod.CASH) {
			if (received != null) {
				throw new BusinessException(InstoreError.INVALID_PAYMENT, "Only a cash payment states an amount received");
			}
			return Money.zero(currency);
		}
		if (received == null) {
			throw new BusinessException(InstoreError.INVALID_PAYMENT, "A cash payment states the amount received");
		}
		Amounts.require(received, currency, false, "The amount received");
		if (received.compareTo(amount) < 0) {
			throw new BusinessException(InstoreError.INVALID_PAYMENT, "The amount received is less than the payment");
		}
		return received.minus(amount);
	}

	private void requireItemsPayment(UUID ticketId, List<TicketLineView> lines, List<UUID> items, Money amount) {
		var covered = payments.coveredLines(ticketId);
		var sum = BigDecimal.ZERO;
		for (var lineId : items) {
			var line = lines.stream()
				.filter(candidate -> candidate.id().equals(lineId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(InstoreError.LINE_NOT_FOUND, "The ticket has no such line"));
			if (line.state() != TicketLineState.SENT) {
				throw new BusinessException(InstoreError.INVALID_PAYMENT, "Line " + lineId + " is not billed");
			}
			if (covered.contains(lineId)) {
				throw new BusinessException(InstoreError.LINE_ALREADY_PAID, "Line " + lineId + " is already paid");
			}
			sum = sum.add(line.lineTotal().amount());
		}
		if (amount.amount().compareTo(sum) != 0) {
			throw new BusinessException(InstoreError.INVALID_PAYMENT, "The payment must equal the total of the chosen lines");
		}
	}

	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A payment needs a signed-in user");
		}
		return id;
	}

}
