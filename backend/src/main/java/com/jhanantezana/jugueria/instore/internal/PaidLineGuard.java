package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;

/** A line that stops being charged (voided or comped) must not leave the live payments above what is owed. */
@Component
class PaidLineGuard {

	private final Tickets tickets;

	private final Payments payments;

	PaidLineGuard(Tickets tickets, Payments payments) {
		this.tickets = tickets;
		this.payments = payments;
	}

	void requireNotCovered(Tickets.TicketRow ticket, TicketLineView line) {
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
