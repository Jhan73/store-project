package com.jhanantezana.jugueria.preparation;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum PreparationError implements ErrorCode {

	ORDER_NOT_FOUND("preparation.order-not-found", HttpStatus.NOT_FOUND),
	LINE_NOT_FOUND("preparation.line-not-found", HttpStatus.NOT_FOUND),
	MARKER_NOT_FOUND("preparation.marker-not-found", HttpStatus.NOT_FOUND),
	ORDER_CANCELLED("preparation.order-cancelled", HttpStatus.CONFLICT),
	ORDER_ALREADY_STARTED("preparation.order-already-started", HttpStatus.CONFLICT),
	ORDER_NOT_PREPARING("preparation.order-not-preparing", HttpStatus.CONFLICT),
	ORDER_NOT_READY("preparation.order-not-ready", HttpStatus.CONFLICT),
	ORDER_NOT_COMPLETED("preparation.order-not-completed", HttpStatus.CONFLICT),
	ORDER_CLOSED("preparation.order-closed", HttpStatus.CONFLICT),
	RECALL_WINDOW_EXPIRED("preparation.recall-window-expired", HttpStatus.CONFLICT),
	LINE_NOT_PENDING("preparation.line-not-pending", HttpStatus.CONFLICT),
	LINE_ALREADY_READY("preparation.line-already-ready", HttpStatus.CONFLICT),
	LINE_ALREADY_FLAGGED("preparation.line-already-flagged", HttpStatus.CONFLICT),
	LINE_FLAGGED_UNAVAILABLE("preparation.line-flagged-unavailable", HttpStatus.CONFLICT),
	LINES_NOT_ACKNOWLEDGED("preparation.lines-not-acknowledged", HttpStatus.CONFLICT),
	MARKER_ALREADY_ACKNOWLEDGED("preparation.marker-already-acknowledged", HttpStatus.CONFLICT);

	private final String code;

	private final HttpStatus status;

	PreparationError(String code, HttpStatus status) {
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
