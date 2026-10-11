package com.jhanantezana.jugueria.preparation.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("jugueria.preparation")
@Validated
record PreparationProperties(@DefaultValue("30m") @NotNull Duration recallWindow) {

	PreparationProperties {
		if (recallWindow.isZero() || recallWindow.isNegative()) {
			throw new IllegalArgumentException("jugueria.preparation.recall-window must be positive");
		}
	}

}
