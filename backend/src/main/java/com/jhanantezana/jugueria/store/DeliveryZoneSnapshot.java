package com.jhanantezana.jugueria.store;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

public record DeliveryZoneSnapshot(String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
		@Nullable Money freeDeliveryThreshold) {
}
