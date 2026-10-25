package com.jhanantezana.jugueria.shared.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

// minAge keeps a run from resubmitting a delivery that is still in flight on this or another task. A publication that
// failed maxAttempts resubmissions is parked, and one older than maxAge is reported as an error on every claim.
@ConfigurationProperties("jugueria.shared.event-resubmission")
@Validated
record EventResubmissionProperties(@DefaultValue("1m") @NotNull Duration interval,
		@DefaultValue("1m") @NotNull Duration initialDelay, @DefaultValue("1m") @NotNull Duration minAge,
		@DefaultValue("5") int maxAttempts, @DefaultValue("1h") @NotNull Duration maxAge) {

	EventResubmissionProperties {
		if (interval.isZero() || interval.isNegative() || initialDelay.isNegative() || minAge.isNegative()) {
			throw new IllegalArgumentException("jugueria.shared.event-resubmission durations must not be negative");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("jugueria.shared.event-resubmission.max-attempts must be at least 1");
		}
		if (maxAge.isZero() || maxAge.isNegative()) {
			throw new IllegalArgumentException("jugueria.shared.event-resubmission.max-age must be positive");
		}
	}

}
