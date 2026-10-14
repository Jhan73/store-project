package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.SaleRecorded;

@Component
class RecordSalesLinesOnSaleRecorded {

	private final SalesLineRecorder recorder;

	RecordSalesLinesOnSaleRecorded(SalesLineRecorder recorder) {
		this.recorder = recorder;
	}

	@ApplicationModuleListener
	void on(SaleRecorded event) {
		recorder.record(event.ticketId(), event.ticketId(), event.type(), event.quickSale(), event.lines(), false,
				event.actorId(), event.occurredAt());
	}

}
