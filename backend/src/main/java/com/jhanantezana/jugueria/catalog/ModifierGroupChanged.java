package com.jhanantezana.jugueria.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code before} is null when the group was created and {@code after} is null when it was deleted. */
public record ModifierGroupChanged(UUID groupId, @Nullable ModifierGroupSnapshot before,
		@Nullable ModifierGroupSnapshot after, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
