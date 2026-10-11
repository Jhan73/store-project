package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** What staff chose for a line: the product, its modifier options, how many, and the kitchen notes. */
public record LineDraft(UUID productId, List<UUID> optionIds, int quantity, @Nullable String note, boolean allergy) {

	public LineDraft {
		optionIds = List.copyOf(optionIds);
		note = note == null || note.isBlank() ? null : note.strip();
	}

}
