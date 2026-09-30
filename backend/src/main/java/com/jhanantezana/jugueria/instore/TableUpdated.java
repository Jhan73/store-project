package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record TableUpdated(UUID tableId, TableSnapshot before, TableSnapshot after, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
