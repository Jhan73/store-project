package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

/** Every line of the order is ready; it now waits for handover. */
public record OrderReady(UUID boardOrderId, BoardSource source, UUID sourceId, Instant occurredAt) {
}
