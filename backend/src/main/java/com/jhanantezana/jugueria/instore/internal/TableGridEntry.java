package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TableStatus;

public record TableGridEntry(DiningTable table, TableStatus status, @Nullable Instant ticketOpenedAt) {
}
