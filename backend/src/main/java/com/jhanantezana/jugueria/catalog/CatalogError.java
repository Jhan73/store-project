package com.jhanantezana.jugueria.catalog;

import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

public enum CatalogError implements ErrorCode {

	STATION_NOT_FOUND("catalog.station-not-found", HttpStatus.NOT_FOUND),
	STATION_NAME_ALREADY_USED("catalog.station-name-already-used", HttpStatus.CONFLICT),
	UNKNOWN_STATION("catalog.unknown-station", HttpStatus.UNPROCESSABLE_CONTENT),
	CATEGORY_NOT_FOUND("catalog.category-not-found", HttpStatus.NOT_FOUND),
	CATEGORY_NAME_ALREADY_USED("catalog.category-name-already-used", HttpStatus.CONFLICT);

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
