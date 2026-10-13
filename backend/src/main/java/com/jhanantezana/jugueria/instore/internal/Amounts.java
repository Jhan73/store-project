package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.util.Currency;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;

/** The checks every amount typed at the register passes. */
final class Amounts {

	static final BigDecimal MAX = new BigDecimal("9999999999.99");

	private Amounts() {
	}

	/** In the store currency, within what the column holds, and positive unless zero is allowed. */
	static Money require(Money amount, Currency currency, boolean zeroAllowed, String what) {
		if (!amount.currency().equals(currency)) {
			throw invalid(what + " must be in " + currency.getCurrencyCode());
		}
		var sign = amount.amount().signum();
		if (sign < 0 || sign == 0 && !zeroAllowed) {
			throw invalid(what + (zeroAllowed ? " cannot be negative" : " must be greater than zero"));
		}
		if (amount.amount().compareTo(MAX) > 0) {
			throw invalid(what + " is too large");
		}
		return amount;
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(InstoreError.INVALID_AMOUNT, detail);
	}

}
