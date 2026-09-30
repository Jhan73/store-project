package com.jhanantezana.jugueria.catalog.internal;

import java.math.BigDecimal;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;

final class PriceLimits {

	// The largest value numeric(12,2) holds: ten integer digits.
	private static final BigDecimal MAX = new BigDecimal("9999999999.99");

	private PriceLimits() {
	}

	static void requireWithinColumn(Money price) {
		if (price.amount().compareTo(MAX) > 0) {
			throw new BusinessException(CatalogError.INVALID_PRICE, "An amount cannot exceed " + MAX.toPlainString());
		}
	}

}
