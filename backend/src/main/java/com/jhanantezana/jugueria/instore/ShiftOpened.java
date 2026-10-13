package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

public record ShiftOpened(UUID shiftId, UUID cashierId, Money openingFloat, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
