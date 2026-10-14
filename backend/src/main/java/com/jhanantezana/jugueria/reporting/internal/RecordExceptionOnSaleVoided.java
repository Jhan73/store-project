package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.SaleVoided;

@Component
class RecordExceptionOnSaleVoided {

	private final ExceptionEvents exceptions;

	RecordExceptionOnSaleVoided(ExceptionEvents exceptions) {
		this.exceptions = exceptions;
	}

	@ApplicationModuleListener
	void on(SaleVoided event) {
		exceptions.record(new ExceptionEvents.Entry(ExceptionType.SALE_VOID, event.saleVoidId(), event.occurredAt(),
				event.actorId(), event.reasonCode(), event.note(), event.total(), event.ticketId(), null));
	}

}
