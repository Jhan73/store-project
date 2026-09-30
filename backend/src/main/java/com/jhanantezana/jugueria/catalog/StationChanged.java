package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record StationChanged(UUID stationId, CatalogChangeKind kind, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
