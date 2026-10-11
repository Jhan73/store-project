package com.jhanantezana.jugueria.instore.web;

import java.util.List;
import java.util.UUID;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record RepeatLinesRequest(@NotEmpty @Size(max = TicketLimits.MAX_REPEATED_LINES) List<@NotNull UUID> lineIds) {
}
