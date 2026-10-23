package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketLineComped;

@Component
class RecordExceptionOnTicketLineComped {

	private final ExceptionEvents exceptions;

	RecordExceptionOnTicketLineComped(ExceptionEvents exceptions) {
		this.exceptions = exceptions;
	}

	@ApplicationModuleListener
	void on(TicketLineComped event) {
		exceptions.record(new ExceptionEvents.Entry(ExceptionType.COMP, event.lineId(), event.occurredAt(),
				event.actorId(), event.reasonCode(), event.note(), event.amount(), event.ticketId(), null));
	}

}
