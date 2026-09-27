package com.jhanantezana.jugueria.store;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum StoreError implements ErrorCode {

	INVALID_OPENING_HOURS("store.invalid-opening-hours", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_BOARD_THRESHOLDS("store.invalid-board-thresholds", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_SETTINGS_VALUE("store.invalid-settings-value", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_DELIVERY_ZONE("store.invalid-delivery-zone", HttpStatus.UNPROCESSABLE_CONTENT),
	DELIVERY_ZONE_NOT_FOUND("store.delivery-zone-not-found", HttpStatus.NOT_FOUND),
	DELIVERY_ZONE_NAME_ALREADY_USED("store.delivery-zone-name-already-used", HttpStatus.CONFLICT),
	REASON_NOT_FOUND("store.reason-not-found", HttpStatus.NOT_FOUND),
	REASON_CODE_ALREADY_USED("store.reason-code-already-used", HttpStatus.CONFLICT);

	private final String code;

	private final HttpStatus status;

	StoreError(String code, HttpStatus status) {
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
