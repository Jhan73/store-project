package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code before} is null when the station was created. */
public record StationChanged(UUID stationId, CatalogChangeKind kind, @Nullable StationSnapshot before,
		StationSnapshot after, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
