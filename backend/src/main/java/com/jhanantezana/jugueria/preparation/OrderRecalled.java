package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

/** A ready or handed-over order went back to preparing. */
public record OrderRecalled(UUID boardOrderId, BoardSource source, UUID sourceId, Instant occurredAt) {
}
