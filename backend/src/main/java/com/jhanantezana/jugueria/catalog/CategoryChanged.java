package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code before} is null when the category was created. */
public record CategoryChanged(UUID categoryId, CatalogChangeKind kind, @Nullable CategorySnapshot before,
		CategorySnapshot after, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
