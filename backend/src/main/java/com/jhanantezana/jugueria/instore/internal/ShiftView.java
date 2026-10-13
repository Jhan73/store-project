package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.shared.Money;

/**
 * Everything after {@code openedAt} is null while the shift is open: expected cash, the difference and the totals
 * are only known once the count has been given.
 */
public record ShiftView(UUID id, UUID cashierId, ShiftStatus status, Money openingFloat, Instant openedAt,
		@Nullable Instant closedAt, @Nullable ShiftCloseKind closeKind, @Nullable Money countedCash,
		@Nullable Money expectedCash, @Nullable Money difference, @Nullable String differenceNote,
		@Nullable String forceReason, @Nullable UUID closedBy, @Nullable ShiftTotals totals) {
}
