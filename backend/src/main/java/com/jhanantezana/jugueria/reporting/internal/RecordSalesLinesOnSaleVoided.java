package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.SaleVoided;

@Component
class RecordSalesLinesOnSaleVoided {

	private final SalesLineRecorder recorder;

	RecordSalesLinesOnSaleVoided(SalesLineRecorder recorder) {
		this.recorder = recorder;
	}

	@ApplicationModuleListener
	void on(SaleVoided event) {
		recorder.record(event.saleVoidId(), event.ticketId(), event.ticketType(), event.quickSale(), event.lines(), true,
				event.actorId(), event.occurredAt());
	}

}
