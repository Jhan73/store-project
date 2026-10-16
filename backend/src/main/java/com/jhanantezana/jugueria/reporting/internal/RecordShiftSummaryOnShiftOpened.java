package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftOpened;

@Component
class RecordShiftSummaryOnShiftOpened {

	private final ShiftSummaries summaries;

	RecordShiftSummaryOnShiftOpened(ShiftSummaries summaries) {
		this.summaries = summaries;
	}

	@ApplicationModuleListener
	void on(ShiftOpened event) {
		summaries.opened(event);
	}

}
