package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.instore.internal.TicketLimits;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code reasonCode} is the code of an active cash-out reason and only for {@code OUT}; cash in explains itself in the
 * {@code note}. The reason "Other" needs a {@code note}.
 */
record CashMovementRequest(@NotNull CashMovementType type, @NotNull Money amount,
		@Nullable @Size(max = 60) String reasonCode, @Nullable @Size(max = TicketLimits.MAX_REASON_NOTE) String note,
		@Nullable @Size(max = 100) String reference) {
}
