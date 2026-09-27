package com.jhanantezana.jugueria.store;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

public record DeliveryZoneView(UUID id, String name, Money fee, int deliveryMinutes,
		@Nullable Money minimumOrder, @Nullable Money freeDeliveryThreshold, boolean active) {
}
