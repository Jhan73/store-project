package com.jhanantezana.jugueria.reporting.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

// The lease must outlast one send (the SES call times out in seconds) so a live delivery is never taken over.
@ConfigurationProperties("jugueria.reporting")
@Validated
record ReportingProperties(@DefaultValue("5m") @NotNull Duration shiftSummaryEmailLease) {

	ReportingProperties {
		if (shiftSummaryEmailLease.isZero() || shiftSummaryEmailLease.isNegative()) {
			throw new IllegalArgumentException("jugueria.reporting.shift-summary-email-lease must be positive");
		}
	}

}
