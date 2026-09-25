package com.jhanantezana.jugueria.shared;

import org.springframework.http.HttpStatus;

public enum CommonError implements ErrorCode {

	MALFORMED_REQUEST("common.malformed-request", HttpStatus.BAD_REQUEST),
	VALIDATION_FAILED("common.validation-failed", HttpStatus.BAD_REQUEST),
	NOT_FOUND("common.not-found", HttpStatus.NOT_FOUND),
	METHOD_NOT_ALLOWED("common.method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
	NOT_ACCEPTABLE("common.not-acceptable", HttpStatus.NOT_ACCEPTABLE),
	CONCURRENT_MODIFICATION("common.concurrent-modification", HttpStatus.CONFLICT),
	CONTENT_TOO_LARGE("common.content-too-large", HttpStatus.CONTENT_TOO_LARGE),
	UNSUPPORTED_MEDIA_TYPE("common.unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	INTERNAL_ERROR("common.internal-error", HttpStatus.INTERNAL_SERVER_ERROR),
	SERVICE_UNAVAILABLE("common.service-unavailable", HttpStatus.SERVICE_UNAVAILABLE);

	private final String code;

	private final HttpStatus status;

	CommonError(String code, HttpStatus status) {
		this.code = code;
		this.status = status;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

}
