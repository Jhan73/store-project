package com.jhanantezana.jugueria.preparation.internal;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Counts of identical items still to prepare, plus the lines that must be read one by one. */
public record AllDayView(List<Group> groups, List<Individual> individual) {

	public record Group(UUID productId, String displayName, @Nullable String modifiersText, int quantity) {
	}

	public record Individual(UUID lineId, UUID boardOrderId, String shortNumber, UUID productId, String displayName,
			int quantity, @Nullable String modifiersText, @Nullable String note, boolean allergy) {
	}

}
