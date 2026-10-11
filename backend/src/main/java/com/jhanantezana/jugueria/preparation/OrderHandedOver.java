package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

public record OrderHandedOver(UUID boardOrderId, BoardSource source, UUID sourceId, Instant occurredAt) {
}
