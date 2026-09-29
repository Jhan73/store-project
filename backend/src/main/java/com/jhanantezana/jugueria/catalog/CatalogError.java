package com.jhanantezana.jugueria.catalog;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum CatalogError implements ErrorCode {

	STATION_NOT_FOUND("catalog.station-not-found", HttpStatus.NOT_FOUND),
	STATION_NAME_ALREADY_USED("catalog.station-name-already-used", HttpStatus.CONFLICT),
	UNKNOWN_STATION("catalog.unknown-station", HttpStatus.UNPROCESSABLE_CONTENT),
	CATEGORY_NOT_FOUND("catalog.category-not-found", HttpStatus.NOT_FOUND),
	CATEGORY_NAME_ALREADY_USED("catalog.category-name-already-used", HttpStatus.CONFLICT),
	UNKNOWN_CATEGORY("catalog.unknown-category", HttpStatus.UNPROCESSABLE_CONTENT),
	MODIFIER_GROUP_NOT_FOUND("catalog.modifier-group-not-found", HttpStatus.NOT_FOUND),
	MODIFIER_GROUP_NAME_ALREADY_USED("catalog.modifier-group-name-already-used", HttpStatus.CONFLICT),
	MODIFIER_GROUP_IN_USE("catalog.modifier-group-in-use", HttpStatus.CONFLICT),
	UNKNOWN_MODIFIER_GROUP("catalog.unknown-modifier-group", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_MODIFIER_GROUP("catalog.invalid-modifier-group", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_MODIFIER_SELECTION("catalog.invalid-modifier-selection", HttpStatus.UNPROCESSABLE_CONTENT),
	PRODUCT_NOT_FOUND("catalog.product-not-found", HttpStatus.NOT_FOUND),
	PRODUCT_NAME_ALREADY_USED("catalog.product-name-already-used", HttpStatus.CONFLICT),
	INVALID_PRODUCT("catalog.invalid-product", HttpStatus.UNPROCESSABLE_CONTENT),
	INVALID_PRICE("catalog.invalid-price", HttpStatus.UNPROCESSABLE_CONTENT),
	CURRENCY_MISMATCH("catalog.currency-mismatch", HttpStatus.UNPROCESSABLE_CONTENT);

	private final String code;

	private final HttpStatus status;

	CatalogError(String code, HttpStatus status) {
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
