package com.jhanantezana.jugueria.instore.web;

import java.util.List;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.shared.Money;

record ShiftTotalsResponse(Money cashReceived, Money changeGiven, Money voidedCash, Money cashIn, Money cashOut,
		List<MethodTotalResponse> byMethod) {

	record MethodTotalResponse(PaymentMethod method, Money recorded, Money voided, Money net) {
	}

	static ShiftTotalsResponse from(ShiftTotals totals) {
		return new ShiftTotalsResponse(totals.cashReceived(), totals.changeGiven(), totals.voidedCash(),
				totals.cashIn(), totals.cashOut(),
				totals.byMethod()
					.stream()
					.map(m -> new MethodTotalResponse(m.method(), m.recorded(), m.voided(), m.net()))
					.toList());
	}

}
