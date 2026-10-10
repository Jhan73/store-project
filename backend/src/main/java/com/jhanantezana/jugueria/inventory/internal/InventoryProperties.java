package com.jhanantezana.jugueria.inventory.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("jugueria.inventory")
@Validated
record InventoryProperties(@DefaultValue("1m") @NotNull Duration reservationSweepInterval,
		@DefaultValue("1m") @NotNull Duration reservationSweepInitialDelay) {

	InventoryProperties {
		if (reservationSweepInterval.isZero() || reservationSweepInterval.isNegative()
				|| reservationSweepInitialDelay.isNegative()) {
			throw new IllegalArgumentException("jugueria.inventory sweep interval must be positive");
		}
	}

}
