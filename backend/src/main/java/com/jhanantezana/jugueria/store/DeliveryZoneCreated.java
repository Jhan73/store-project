package com.jhanantezana.jugueria.store;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

public record DeliveryZoneCreated(UUID zoneId, String name, Money fee, int deliveryMinutes,
		@Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
