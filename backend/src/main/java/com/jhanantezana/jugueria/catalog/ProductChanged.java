package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code before} is null when the product was created. Availability has its own event. */
public record ProductChanged(UUID productId, @Nullable ProductSnapshot before, ProductSnapshot after,
		@Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
