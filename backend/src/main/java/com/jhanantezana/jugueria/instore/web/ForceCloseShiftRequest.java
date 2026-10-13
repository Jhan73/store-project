package com.jhanantezana.jugueria.instore.web;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ForceCloseShiftRequest(@NotBlank @Size(max = TicketLimits.MAX_REASON_NOTE) String reason) {
}
