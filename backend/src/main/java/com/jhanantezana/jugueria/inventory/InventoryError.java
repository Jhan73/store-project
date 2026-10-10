package com.jhanantezana.jugueria.inventory;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum InventoryError implements ErrorCode {

	PRODUCT_NOT_FOUND("inventory.product-not-found", HttpStatus.NOT_FOUND),
	STOCK_ITEM_NOT_FOUND("inventory.stock-item-not-found", HttpStatus.NOT_FOUND),
	PRODUCT_NOT_TRACKED("inventory.product-not-tracked", HttpStatus.CONFLICT),
	INSUFFICIENT_STOCK("inventory.insufficient-stock", HttpStatus.CONFLICT),
	STOCK_RESERVED("inventory.stock-reserved", HttpStatus.CONFLICT),
	RESERVATION_NOT_ACTIVE("inventory.reservation-not-active", HttpStatus.CONFLICT),
	INVALID_QUANTITY("inventory.invalid-quantity", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_REASON("inventory.invalid-reason", HttpStatus.UNPROCESSABLE_CONTENT);

	private final String code;

	private final HttpStatus status;

	InventoryError(String code, HttpStatus status) {
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
