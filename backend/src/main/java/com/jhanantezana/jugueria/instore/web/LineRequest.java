package com.jhanantezana.jugueria.instore.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.LineDraft;
import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The modifier options are the product's chosen options; the catalog validates them against its groups. */
record LineRequest(@NotNull UUID productId, @Nullable @Size(max = TicketLimits.MAX_OPTIONS) List<@NotNull UUID> modifierOptionIds,
		@Min(1) @Max(TicketLimits.MAX_QUANTITY) int quantity, @Nullable @Size(max = TicketLimits.MAX_NOTE) String note,
		boolean allergy) {

	LineDraft toDraft() {
		return new LineDraft(productId, modifierOptionIds == null ? List.of() : modifierOptionIds, quantity, note,
				allergy);
	}

}
