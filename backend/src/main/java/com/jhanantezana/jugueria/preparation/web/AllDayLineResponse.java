package com.jhanantezana.jugueria.preparation.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.internal.AllDayView;

record AllDayLineResponse(UUID lineId, UUID boardOrderId, String shortNumber, UUID productId, String displayName,
		int quantity, @Nullable String modifiersText, @Nullable String note, boolean allergy) {

	static AllDayLineResponse from(AllDayView.Individual line) {
		return new AllDayLineResponse(line.lineId(), line.boardOrderId(), line.shortNumber(), line.productId(),
				line.displayName(), line.quantity(), line.modifiersText(), line.note(), line.allergy());
	}

}
