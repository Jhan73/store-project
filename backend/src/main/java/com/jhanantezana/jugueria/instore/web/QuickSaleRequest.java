package com.jhanantezana.jugueria.instore.web;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The sale is paid in full with one payment; cash states {@code receivedAmount} and no other method does. */
record QuickSaleRequest(@Nullable @Size(max = TicketLimits.MAX_COUNTER_NAME) String customerName,
		@NotEmpty @Size(max = TicketLimits.MAX_REPEATED_LINES) List<@NotNull @Valid LineRequest> lines,
		@NotNull @Valid QuickSalePayment payment) {
}
