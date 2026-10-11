package com.jhanantezana.jugueria.instore;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum InstoreError implements ErrorCode {

	TABLE_NOT_FOUND("instore.table-not-found", HttpStatus.NOT_FOUND),
	TABLE_NAME_ALREADY_USED("instore.table-name-already-used", HttpStatus.CONFLICT),
	TABLE_OCCUPIED("instore.table-occupied", HttpStatus.CONFLICT),
	TABLE_INACTIVE("instore.table-inactive", HttpStatus.CONFLICT),
	TABLE_HAS_OPEN_TICKET("instore.table-has-open-ticket", HttpStatus.CONFLICT),
	TICKET_NOT_FOUND("instore.ticket-not-found", HttpStatus.NOT_FOUND),
	TICKET_CLOSED("instore.ticket-closed", HttpStatus.CONFLICT),
	INVALID_TICKET("instore.invalid-ticket", HttpStatus.UNPROCESSABLE_CONTENT),
	TICKET_HAS_NO_TABLE("instore.ticket-has-no-table", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_NOT_FOUND("instore.line-not-found", HttpStatus.NOT_FOUND),
	LINE_ALREADY_SENT("instore.line-already-sent", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_NOT_SENT("instore.line-not-sent", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_REASON("instore.invalid-reason", HttpStatus.UNPROCESSABLE_CONTENT);

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
