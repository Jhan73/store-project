package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.shared.Money;

public record CashMovementView(UUID id, UUID shiftId, CashMovementType type, Money amount,
		@Nullable String reasonCode, @Nullable String note, @Nullable String reference, UUID recordedBy,
		Instant recordedAt) {
}
