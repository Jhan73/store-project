package com.jhanantezana.jugueria.store.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.internal.DeliveryZone;

record DeliveryZoneResponse(UUID id, String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
		@Nullable Money freeDeliveryThreshold, boolean active) {

	static DeliveryZoneResponse from(DeliveryZone zone) {
		return new DeliveryZoneResponse(zone.getId(), zone.getName(), zone.getFee(), zone.getDeliveryMinutes(),
				zone.getMinimumOrder(), zone.getFreeDeliveryThreshold(), zone.isActive());
	}

}
