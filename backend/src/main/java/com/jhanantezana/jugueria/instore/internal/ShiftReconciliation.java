package com.jhanantezana.jugueria.instore.internal;

import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.shared.Money;

/** The arithmetic of a shift's cash count. */
final class ShiftReconciliation {

	private ShiftReconciliation() {
	}

	/** Float plus cash taken, minus change given and cash paid back, plus cash in, minus cash out. */
	static Money expectedCash(Money openingFloat, ShiftTotals totals) {
		return openingFloat.plus(totals.cashReceived())
			.minus(totals.changeGiven())
			.minus(totals.voidedCash())
			.plus(totals.cashIn())
			.minus(totals.cashOut());
	}

	/** Positive when the drawer holds more than expected, negative when it is short. */
	static Money difference(Money counted, Money expected) {
		return counted.minus(expected);
	}

	static boolean needsNote(Money difference, Money threshold) {
		return difference.amount().abs().compareTo(threshold.amount()) > 0;
	}

}
