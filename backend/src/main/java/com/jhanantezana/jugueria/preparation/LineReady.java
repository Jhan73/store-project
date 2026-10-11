package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

public record LineReady(UUID boardOrderId, UUID lineId, UUID sourceLineId, BoardSource source, UUID sourceId,
		Instant occurredAt) {
}
