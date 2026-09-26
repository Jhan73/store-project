package com.jhanantezana.jugueria.identity;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum AuthError implements ErrorCode {

	UNAUTHENTICATED("auth.unauthenticated", HttpStatus.UNAUTHORIZED),
	FORBIDDEN("auth.forbidden", HttpStatus.FORBIDDEN),
	// Unknown user, wrong password, and inactive account all answer this same code: an attacker must
	// not learn which reason applied.
	INVALID_CREDENTIALS("auth.invalid-credentials", HttpStatus.UNAUTHORIZED),
	// A locked account is disclosed as such, unlike the cases above: the client can tell the user when
	// to retry instead of asking them to keep guessing a password that is no longer even checked.
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
