package com.jhanantezana.jugueria.shared;

import java.util.Optional;

import org.slf4j.MDC;

public final class CorrelationId {

	public static final String HEADER = "X-Request-Id";

	public static final String MDC_KEY = "correlation_id";

	private CorrelationId() {
	}

	public static Optional<String> current() {
		return Optional.ofNullable(MDC.get(MDC_KEY));
	}

}
