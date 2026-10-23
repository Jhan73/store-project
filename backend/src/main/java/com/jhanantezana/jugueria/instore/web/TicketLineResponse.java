package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.TicketLineState;
import com.jhanantezana.jugueria.instore.internal.TicketLineView;
import com.jhanantezana.jugueria.shared.Money;

record TicketLineResponse(UUID id, UUID productId, String productName, int quantity, Money unitPrice, Money lineTotal,
		List<ModifierResponse> modifiers, @Nullable String note, boolean allergy, TicketLineState state,
		@Nullable Instant sentAt, boolean waste, @Nullable String voidReasonCode, @Nullable String voidNote,
		@Nullable String compReasonCode, @Nullable String compNote) {

	record ModifierResponse(UUID optionId, String name, Money priceDelta) {
	}

	static TicketLineResponse from(TicketLineView line) {
		return new TicketLineResponse(line.id(), line.productId(), line.productName(), line.quantity(), line.unitPrice(),
				line.lineTotal(),
				line.modifiers().stream().map(o -> new ModifierResponse(o.optionId(), o.name(), o.priceDelta())).toList(),
				line.note(), line.allergy(), line.state(), line.sentAt(), line.waste(), line.voidReasonCode(),
				line.voidNote(), line.compReasonCode(), line.compNote());
	}

}
