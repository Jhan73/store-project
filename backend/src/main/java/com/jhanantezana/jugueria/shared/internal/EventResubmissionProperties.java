package com.jhanantezana.jugueria.shared.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

// minAge keeps a run from resubmitting a delivery that is still in flight on this or another task.
@ConfigurationProperties("jugueria.shared.event-resubmission")
@Validated
record EventResubmissionProperties(@DefaultValue("1m") @NotNull Duration interval,
		@DefaultValue("1m") @NotNull Duration initialDelay, @DefaultValue("1m") @NotNull Duration minAge) {

	EventResubmissionProperties {
		if (interval.isZero() || interval.isNegative() || initialDelay.isNegative() || minAge.isNegative()) {
			throw new IllegalArgumentException("jugueria.shared.event-resubmission durations must not be negative");
		}
	}

}
