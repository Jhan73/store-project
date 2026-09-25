package com.jhanantezana.jugueria.identity;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum AuthError implements ErrorCode {

	UNAUTHENTICATED("auth.unauthenticated", HttpStatus.UNAUTHORIZED),
	FORBIDDEN("auth.forbidden", HttpStatus.FORBIDDEN);

	private final String code;

	private final HttpStatus status;

	AuthError(String code, HttpStatus status) {
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
