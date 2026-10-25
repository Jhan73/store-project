package com.jhanantezana.jugueria.store.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.internal.DeliveryZone;

record DeliveryZoneResponse(UUID id, String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
		@Nullable Money freeDeliveryThreshold, boolean active, boolean deletable, String etag) {

	static DeliveryZoneResponse from(DeliveryZone zone, boolean deletable) {
		return new DeliveryZoneResponse(zone.getId(), zone.getName(), zone.getFee(), zone.getDeliveryMinutes(),
				zone.getMinimumOrder(), zone.getFreeDeliveryThreshold(), zone.isActive(), deletable,
				ETags.format(zone.getVersion()));
	}

}
