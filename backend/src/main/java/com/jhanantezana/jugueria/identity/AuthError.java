package com.jhanantezana.jugueria.identity;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum AuthError implements ErrorCode {

	UNAUTHENTICATED("auth.unauthenticated", HttpStatus.UNAUTHORIZED),
	FORBIDDEN("auth.forbidden", HttpStatus.FORBIDDEN),
	// Shared by unknown user, wrong password, and inactive account so none is revealed.
	INVALID_CREDENTIALS("auth.invalid-credentials", HttpStatus.UNAUTHORIZED),
	// Disclosed so the client can tell the user when to retry.
	ACCOUNT_LOCKED("auth.account-locked", HttpStatus.CONFLICT);

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
