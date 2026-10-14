package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.PaymentVoided;

// A payment voided as part of a sale void is already counted by the sale void.
@Component
class RecordExceptionOnPaymentVoided {

	private final ExceptionEvents exceptions;

	RecordExceptionOnPaymentVoided(ExceptionEvents exceptions) {
		this.exceptions = exceptions;
	}

	@ApplicationModuleListener
	void on(PaymentVoided event) {
		if (event.saleVoidId() != null) {
			return;
		}
		exceptions.record(new ExceptionEvents.Entry(ExceptionType.PAYMENT_VOID, event.paymentId(), event.occurredAt(),
				event.actorId(), event.reasonCode(), event.note(), event.amount(), event.ticketId(), event.shiftId()));
	}

}
