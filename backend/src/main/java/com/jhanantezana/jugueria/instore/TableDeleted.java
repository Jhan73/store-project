package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** The table is gone, so the event carries everything it was. */
public record TableDeleted(UUID tableId, TableSnapshot before, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
