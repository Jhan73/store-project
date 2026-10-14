package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftClosed;

// The difference belongs to the cashier even when an admin did the closing, and a forced close has no count.
@Component
class RecordExceptionOnShiftClosed {

	private final ExceptionEvents exceptions;

	RecordExceptionOnShiftClosed(ExceptionEvents exceptions) {
		this.exceptions = exceptions;
	}

	@ApplicationModuleListener
	void on(ShiftClosed event) {
		var difference = event.difference();
		if (difference == null || difference.isZero()) {
			return;
		}
		exceptions.record(new ExceptionEvents.Entry(ExceptionType.SHIFT_DIFFERENCE, event.shiftId(), event.occurredAt(),
				event.cashierId(), null, event.differenceNote(), difference, null, event.shiftId()));
	}

}
