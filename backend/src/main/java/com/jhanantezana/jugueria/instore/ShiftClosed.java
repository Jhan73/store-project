package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/**
 * {@code countedCash} and {@code difference} are null when an ADMIN force-closed the shift, which carries a
 * {@code forceReason} instead; {@code differenceNote} is the cashier's note for a difference over the threshold.
 */
public record ShiftClosed(UUID shiftId, UUID cashierId, boolean forced, Money openingFloat,
		@Nullable Money countedCash, Money expectedCash, @Nullable Money difference, @Nullable String differenceNote,
		@Nullable String forceReason, ShiftTotals totals, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
