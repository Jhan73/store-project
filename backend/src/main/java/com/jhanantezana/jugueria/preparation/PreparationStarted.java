package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

/** Staff started an order (also when a recalled order goes back to preparing; see {@link OrderRecalled}). */
public record PreparationStarted(UUID boardOrderId, BoardSource source, UUID sourceId, Instant occurredAt) {
}
