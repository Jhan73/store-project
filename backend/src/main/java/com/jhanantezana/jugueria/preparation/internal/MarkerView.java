package com.jhanantezana.jugueria.preparation.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record MarkerView(UUID id, UUID boardOrderId, @Nullable UUID lineId, MarkerKind kind, Instant createdAt,
		@Nullable Instant acknowledgedAt) {
}
