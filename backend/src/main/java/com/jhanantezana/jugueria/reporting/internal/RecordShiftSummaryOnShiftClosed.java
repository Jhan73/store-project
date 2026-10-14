package com.jhanantezana.jugueria.reporting.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftClosed;

@Component
class RecordShiftSummaryOnShiftClosed {

	private final ShiftSummaries summaries;

	RecordShiftSummaryOnShiftClosed(ShiftSummaries summaries) {
		this.summaries = summaries;
	}

	@ApplicationModuleListener
	void on(ShiftClosed event) {
		summaries.closed(event);
	}

}
