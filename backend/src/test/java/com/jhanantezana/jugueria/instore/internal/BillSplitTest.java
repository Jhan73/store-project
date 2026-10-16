package com.jhanantezana.jugueria.instore.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.Money;

class BillSplitTest {

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void anAmountThatDividesExactlyGivesEqualParts() {
		assertThat(BillSplit.evenly(pen("30.00"), 3)).containsExactly(pen("10.00"), pen("10.00"), pen("10.00"));
	}

	@Test
	void theFirstPartsAreRoundedDownToCentsAndTheLastTakesTheRemainder() {
		assertThat(BillSplit.evenly(pen("100.00"), 3)).containsExactly(pen("33.33"), pen("33.33"), pen("33.34"));
		assertThat(BillSplit.evenly(pen("10.00"), 6)).containsExactly(pen("1.66"), pen("1.66"), pen("1.66"), pen("1.66"),
				pen("1.66"), pen("1.70"));
	}

	@Test
	void theLastPartCanBeSmallerThanTheOthersNever() {
		var parts = BillSplit.evenly(pen("0.07"), 3);

		assertThat(parts).containsExactly(pen("0.02"), pen("0.02"), pen("0.03"));
	}

	@Test
	void thePartsAlwaysAddUpToTheAmount() {
		for (var cents = 100; cents <= 5000; cents += 37) {
			for (var count = 2; count <= 12; count++) {
				var amount = new Money(java.math.BigDecimal.valueOf(cents, 2), PEN);

				var total = BillSplit.evenly(amount, count).stream().reduce(Money.zero(PEN), Money::plus);

				assertThat(total).isEqualByComparingTo(amount);
			}
		}
	}

	@Test
	void aSplitNeedsAtLeastTwoPartsAndAPositiveAmount() {
		assertThatThrownBy(() -> BillSplit.evenly(pen("10.00"), 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BillSplit.evenly(Money.zero(PEN), 2)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anAmountSmallerThanOneCentPerPartLeavesTheEarlierPartsEmptyAndIsRejected() {
		assertThatThrownBy(() -> BillSplit.evenly(pen("0.02"), 3)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void returnsOnePartPerGuest() {
		List<Money> parts = BillSplit.evenly(pen("9.99"), 4);

		assertThat(parts).hasSize(4);
	}

	private static Money pen(String amount) {
		return Money.of(amount, PEN);
	}

}
