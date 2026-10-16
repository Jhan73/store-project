package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.jhanantezana.jugueria.shared.Money;

/** Splitting a bill evenly, the one place that decides who absorbs the cents. */
final class BillSplit {

	private BillSplit() {
	}

	/** {@code parts - 1} equal parts rounded down to cents, and the last one takes the remainder. */
	static List<Money> evenly(Money amount, int parts) {
		if (parts < 2) {
			throw new IllegalArgumentException("A split needs at least two parts");
		}
		if (amount.amount().signum() <= 0) {
			throw new IllegalArgumentException("Only a positive amount can be split");
		}
		var each = amount.amount().divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
		if (each.signum() == 0) {
			throw new IllegalArgumentException("The amount is too small to give every part a cent");
		}
		var split = new ArrayList<Money>(parts);
		var equal = new Money(each, amount.currency());
		for (var i = 0; i < parts - 1; i++) {
			split.add(equal);
		}
		split.add(amount.minus(equal.times(parts - 1)));
		return List.copyOf(split);
	}

}
