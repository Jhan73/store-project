package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

public record TicketLineView(UUID id, UUID productId, String productName, int quantity, Money unitPrice,
		List<ChosenOption> modifiers, @Nullable String note, boolean allergy, TicketLineState state,
		@Nullable Instant sentAt, boolean waste, @Nullable String voidReasonCode, @Nullable String voidNote,
		@Nullable String compReasonCode, @Nullable String compNote) {

	public Money lineTotal() {
		return unitPrice.times(quantity);
	}

}
