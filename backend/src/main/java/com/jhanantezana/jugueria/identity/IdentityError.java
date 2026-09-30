package com.jhanantezana.jugueria.identity;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum IdentityError implements ErrorCode {

	EMAIL_ALREADY_REGISTERED("identity.email-already-registered", HttpStatus.CONFLICT),
	INVALID_STAFF_ROLE("identity.invalid-staff-role", HttpStatus.UNPROCESSABLE_CONTENT),
	// The actor themselves, not a business rule about the target account.
	CANNOT_MODIFY_OWN_ACCOUNT("identity.cannot-modify-own-account", HttpStatus.UNPROCESSABLE_CONTENT),
	// The number of active ADMINs, which someone else promoting another admin can change.
	LAST_ACTIVE_ADMIN_REQUIRED("identity.last-active-admin-required", HttpStatus.CONFLICT);

	private final String code;

	private final HttpStatus status;

	IdentityError(String code, HttpStatus status) {
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
