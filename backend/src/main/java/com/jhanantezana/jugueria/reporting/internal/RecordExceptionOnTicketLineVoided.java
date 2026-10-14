package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketLineVoided;

@Component
class RecordExceptionOnTicketLineVoided {

	private final ExceptionEvents exceptions;

	RecordExceptionOnTicketLineVoided(ExceptionEvents exceptions) {
		this.exceptions = exceptions;
	}

	@ApplicationModuleListener
	void on(TicketLineVoided event) {
		var type = event.waste() ? ExceptionType.WASTE : ExceptionType.LINE_VOID;
		exceptions.record(new ExceptionEvents.Entry(type, event.lineId(), event.occurredAt(), event.actorId(),
				event.reasonCode(), event.note(), null, event.ticketId(), null));
	}

}
