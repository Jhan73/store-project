package com.jhanantezana.jugueria.notifications.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

// The longest a deactivated user's open STOMP session can survive is the access token lifetime plus one sweep.
@ConfigurationProperties("jugueria.notifications.session-recheck")
@Validated
record StompSessionRecheckProperties(@DefaultValue("30s") @NotNull Duration sweepInterval,
		@DefaultValue("5m") @NotNull Duration accountRecheckInterval) {

	StompSessionRecheckProperties {
		if (sweepInterval.isZero() || sweepInterval.isNegative() || accountRecheckInterval.isZero()
				|| accountRecheckInterval.isNegative()) {
			throw new IllegalArgumentException("jugueria.notifications.session-recheck durations must be positive");
		}
	}

}
