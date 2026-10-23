package com.jhanantezana.jugueria.inventory;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** A sale took more units than the shelf had: {@code shortBy} of them were never in stock and {@code onHand} is below zero. */
public record StockDeficit(UUID productId, UUID referenceId, int shortBy, int onHand, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
