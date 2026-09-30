package com.jhanantezana.jugueria.shared;

import java.util.Map;
import java.util.Objects;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final Map<String, Object> properties;

	public BusinessException(ErrorCode errorCode, String detail) {
		this(errorCode, detail, Map.of());
	}

	public BusinessException(ErrorCode errorCode, String detail, Map<String, Object> properties) {
		super(detail);
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
		this.properties = Map.copyOf(properties);
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public Map<String, Object> properties() {
		return properties;
	}

}
