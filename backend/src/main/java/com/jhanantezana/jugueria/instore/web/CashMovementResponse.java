package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.instore.internal.CashMovementView;
import com.jhanantezana.jugueria.shared.Money;

record CashMovementResponse(UUID id, UUID shiftId, CashMovementType type, Money amount, @Nullable String reasonCode,
		@Nullable String note, @Nullable String reference, UUID recordedBy, Instant recordedAt) {

	static CashMovementResponse from(CashMovementView movement) {
		return new CashMovementResponse(movement.id(), movement.shiftId(), movement.type(), movement.amount(),
				movement.reasonCode(), movement.note(), movement.reference(), movement.recordedBy(), movement.recordedAt());
	}

}
