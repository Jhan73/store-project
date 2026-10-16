package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code note} explains a difference over the threshold the store configured. */
record CloseShiftRequest(@NotNull Money countedCash, @Nullable @Size(max = TicketLimits.MAX_REASON_NOTE) String note) {
}
