package com.jhanantezana.jugueria.instore;

import java.util.Currency;
import java.util.List;

import com.jhanantezana.jugueria.shared.Money;

/**
 * What moved through a shift's drawer and what was taken per payment method. {@code cashReceived} is what customers
 * handed over before change; {@code voidedCash} is the cash paid back for payments voided during the shift.
 */
public record ShiftTotals(Money cashReceived, Money changeGiven, Money voidedCash, Money cashIn, Money cashOut,
		List<MethodTotal> byMethod) {

	public ShiftTotals {
		byMethod = byMethod == null ? List.of() : List.copyOf(byMethod);
	}

	/** Per method: what was recorded, what was voided and the difference. */
	public record MethodTotal(PaymentMethod method, Money recorded, Money voided, Money net) {
	}

	public static ShiftTotals empty(Currency currency) {
		var zero = Money.zero(currency);
		return new ShiftTotals(zero, zero, zero, zero, zero, List.of());
	}

}
