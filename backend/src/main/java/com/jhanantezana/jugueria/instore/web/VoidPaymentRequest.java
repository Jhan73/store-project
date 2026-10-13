package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code reason} is the code of an active void reason; the reason "Other" needs a {@code note}. */
record VoidPaymentRequest(@NotBlank @Size(max = 60) String reason,
		@Nullable @Size(max = TicketLimits.MAX_REASON_NOTE) String note) {
}
