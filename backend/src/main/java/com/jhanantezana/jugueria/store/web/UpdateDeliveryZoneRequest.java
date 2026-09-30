package com.jhanantezana.jugueria.store.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record UpdateDeliveryZoneRequest(@NotBlank String name, @NotNull Money fee, @Positive int deliveryMinutes,
		@Nullable Money minimumOrder, @Nullable Money freeDeliveryThreshold) {
}
