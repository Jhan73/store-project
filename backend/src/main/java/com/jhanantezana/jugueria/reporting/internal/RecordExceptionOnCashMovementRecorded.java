package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.CashMovementRecorded;
import com.jhanantezana.jugueria.instore.CashMovementType;

@Component
class RecordExceptionOnCashMovementRecorded {

	private final ExceptionEvents exceptions;

	RecordExceptionOnCashMovementRecorded(ExceptionEvents exceptions) {
		this.exceptions = exceptions;
	}

	@ApplicationModuleListener
	void on(CashMovementRecorded event) {
		if (event.type() != CashMovementType.OUT) {
			return;
		}
		exceptions.record(new ExceptionEvents.Entry(ExceptionType.CASH_OUT, event.movementId(), event.occurredAt(),
				event.actorId(), event.reasonCode(), event.note(), event.amount(), null, event.shiftId()));
	}

}
