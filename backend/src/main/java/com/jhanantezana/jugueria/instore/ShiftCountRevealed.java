package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/** The cashier was shown the difference of a count over the threshold, so the count can no longer change. */
public record ShiftCountRevealed(UUID shiftId, UUID cashierId, Money countedCash, Money difference,
		@Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
