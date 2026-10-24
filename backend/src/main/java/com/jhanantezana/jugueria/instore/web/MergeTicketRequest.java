package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** {@code sourceTicketId} is the open ticket folded into the one in the path. */
record MergeTicketRequest(@NotNull UUID sourceTicketId) {
}
