package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record CategoryChanged(UUID categoryId, CatalogChangeKind kind, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
