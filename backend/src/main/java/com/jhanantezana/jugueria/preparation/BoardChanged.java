package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Something on the board changed; clients re-fetch. {@code type} is the fact in {@code UPPER_SNAKE_CASE}. */
public record BoardChanged(String type, UUID boardOrderId, @Nullable UUID lineId, Instant occurredAt) {
}
