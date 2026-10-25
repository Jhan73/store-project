package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/**
 * {@code before} is null when the product was created and {@code after} is null when it was deleted. Availability
 * has its own event.
 */
public record ProductChanged(UUID productId, @Nullable ProductSnapshot before, @Nullable ProductSnapshot after,
		@Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
