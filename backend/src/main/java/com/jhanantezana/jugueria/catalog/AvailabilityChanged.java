package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** Published only when the flag actually flipped, so a repeated "86" is not a fact worth signalling. */
public record AvailabilityChanged(UUID targetId, AvailabilityTarget target, boolean available,
		@Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
