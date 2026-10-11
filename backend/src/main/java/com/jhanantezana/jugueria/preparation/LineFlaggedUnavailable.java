package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** Staff flagged a line because its item ran out; the order cannot be ready until the line is resolved. */
public record LineFlaggedUnavailable(UUID boardOrderId, UUID lineId, UUID sourceLineId, BoardSource source,
		UUID sourceId, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
