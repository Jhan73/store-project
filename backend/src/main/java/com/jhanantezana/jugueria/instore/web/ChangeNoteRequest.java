package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.TicketLimits;

import jakarta.validation.constraints.Size;

/** The ticket's whole note; blank or {@code null} clears it. */
record ChangeNoteRequest(@Nullable @Size(max = TicketLimits.MAX_NOTE) String note) {
}
