package com.jhanantezana.jugueria.instore;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum InstoreError implements ErrorCode {

	TABLE_NOT_FOUND("instore.table-not-found", HttpStatus.NOT_FOUND),
	TABLE_NAME_ALREADY_USED("instore.table-name-already-used", HttpStatus.CONFLICT),
	TABLE_OCCUPIED("instore.table-occupied", HttpStatus.CONFLICT),
	TABLE_INACTIVE("instore.table-inactive", HttpStatus.CONFLICT),
	TABLE_HAS_OPEN_TICKET("instore.table-has-open-ticket", HttpStatus.CONFLICT),
	TABLE_IN_USE("instore.table-in-use", HttpStatus.CONFLICT),
	WALLET_PROVIDER_NOT_FOUND("instore.wallet-provider-not-found", HttpStatus.NOT_FOUND),
	WALLET_PROVIDER_NAME_ALREADY_USED("instore.wallet-provider-name-already-used", HttpStatus.CONFLICT),
	WALLET_PROVIDER_REQUIRED("instore.wallet-provider-required", HttpStatus.UNPROCESSABLE_CONTENT),
	WALLET_PROVIDER_NOT_ALLOWED("instore.wallet-provider-not-allowed", HttpStatus.UNPROCESSABLE_CONTENT),
	WALLET_PROVIDER_INACTIVE("instore.wallet-provider-inactive", HttpStatus.CONFLICT),
	TICKET_NOT_FOUND("instore.ticket-not-found", HttpStatus.NOT_FOUND),
	TICKET_CLOSED("instore.ticket-closed", HttpStatus.CONFLICT),
	INVALID_TICKET("instore.invalid-ticket", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_DATE_RANGE("instore.invalid-date-range", HttpStatus.UNPROCESSABLE_CONTENT),
	TICKET_NOT_EMPTY("instore.ticket-not-empty", HttpStatus.CONFLICT),
	INVALID_MERGE("instore.invalid-merge", HttpStatus.UNPROCESSABLE_CONTENT),
	TICKET_HAS_PAYMENTS("instore.ticket-has-payments", HttpStatus.CONFLICT),
	TICKET_HAS_NO_TABLE("instore.ticket-has-no-table", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_NOT_FOUND("instore.line-not-found", HttpStatus.NOT_FOUND),
	LINE_ALREADY_SENT("instore.line-already-sent", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_PRODUCT_UNAVAILABLE("instore.line-product-unavailable", HttpStatus.CONFLICT),
	PRODUCT_NOT_QUICK_SALE("instore.product-not-quick-sale", HttpStatus.UNPROCESSABLE_CONTENT),
	TOO_MANY_LINES("instore.too-many-lines", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_NOT_SENT("instore.line-not-sent", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_VOIDED("instore.line-voided", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_COMPED("instore.line-comped", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_REASON("instore.invalid-reason", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_AMOUNT("instore.invalid-amount", HttpStatus.UNPROCESSABLE_CONTENT),
	SHIFT_NOT_FOUND("instore.shift-not-found", HttpStatus.NOT_FOUND),
	SHIFT_ALREADY_OPEN("instore.shift-already-open", HttpStatus.CONFLICT),
	USER_HAS_OPEN_SHIFT("instore.user-has-open-shift", HttpStatus.CONFLICT),
	NO_OPEN_SHIFT("instore.no-open-shift", HttpStatus.CONFLICT),
	SHIFT_CLOSED("instore.shift-closed", HttpStatus.CONFLICT),
	SHIFT_CLOSING("instore.shift-closing", HttpStatus.CONFLICT),
	DIFFERENCE_NOTE_REQUIRED("instore.difference-note-required", HttpStatus.CONFLICT),
	COUNTED_CASH_LOCKED("instore.counted-cash-locked", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_CASH_MOVEMENT("instore.invalid-cash-movement", HttpStatus.UNPROCESSABLE_CONTENT),
	CASH_MOVEMENT_NOT_FOUND("instore.cash-movement-not-found", HttpStatus.NOT_FOUND),
	CASH_OUT_EXCEEDS_DRAWER("instore.cash-out-exceeds-drawer", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_PAYMENT("instore.invalid-payment", HttpStatus.UNPROCESSABLE_CONTENT),
	PAYMENT_EXCEEDS_BALANCE("instore.payment-exceeds-balance", HttpStatus.UNPROCESSABLE_CONTENT),
	TICKET_HAS_UNSENT_LINES("instore.ticket-has-unsent-lines", HttpStatus.UNPROCESSABLE_CONTENT),
	LINE_ALREADY_PAID("instore.line-already-paid", HttpStatus.CONFLICT),
	LINE_COVERED_BY_PAYMENT("instore.line-covered-by-payment", HttpStatus.CONFLICT),
	PAYMENT_NOT_FOUND("instore.payment-not-found", HttpStatus.NOT_FOUND),
	TICKET_NOT_CLOSED("instore.ticket-not-closed", HttpStatus.CONFLICT),
	SALE_VOID_NOT_PERMITTED("instore.sale-void-not-permitted", HttpStatus.FORBIDDEN);

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
