package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

public record CashMovementRecorded(UUID movementId, UUID shiftId, CashMovementType type, Money amount,
		@Nullable String reasonCode, @Nullable String note, @Nullable String reference, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
