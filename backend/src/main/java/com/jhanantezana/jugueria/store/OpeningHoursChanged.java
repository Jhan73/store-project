package com.jhanantezana.jugueria.store;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

public record OpeningHoursChanged(UUID settingsId, List<OpeningHourSnapshot> before,
		List<OpeningHourSnapshot> after, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
