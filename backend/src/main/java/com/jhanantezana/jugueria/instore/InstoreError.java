package com.jhanantezana.jugueria.instore;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum InstoreError implements ErrorCode {

	TABLE_NOT_FOUND("instore.table-not-found", HttpStatus.NOT_FOUND),
	TABLE_NAME_ALREADY_USED("instore.table-name-already-used", HttpStatus.CONFLICT);

	private final String code;

	private final HttpStatus status;

	InstoreError(String code, HttpStatus status) {
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
