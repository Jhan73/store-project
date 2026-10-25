package com.jhanantezana.jugueria.store;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** The zone is gone, so the event carries everything it was. */
public record DeliveryZoneDeleted(UUID zoneId, DeliveryZoneSnapshot before, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
