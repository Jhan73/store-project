package com.jhanantezana.jugueria.shared.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("jugueria.shared.idempotency")
@Validated
record IdempotencyProperties(@DefaultValue("24h") @NotNull Duration ttl,
		@DefaultValue("2s") @NotNull Duration lockTimeout, @DefaultValue("1h") @NotNull Duration cleanupInterval) {

	IdempotencyProperties {
		if (ttl.isZero() || ttl.isNegative() || lockTimeout.isZero() || lockTimeout.isNegative()
				|| cleanupInterval.isZero() || cleanupInterval.isNegative()) {
			throw new IllegalArgumentException("jugueria.shared.idempotency durations must be positive");
		}
	}

}
