package com.jhanantezana.jugueria.instore.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.shared.Money;

class ShiftReconciliationTest {

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void expectedCashIsTheFloatPlusCashSalesMinusChangeVoidedCashAndCashOutPlusCashIn() {
		var totals = new ShiftTotals(pen("200.00"), pen("20.00"), pen("15.00"), pen("30.00"), pen("50.00"), List.of());

		var expected = ShiftReconciliation.expectedCash(pen("100.00"), totals);

		// 100 + 200 - 20 - 15 + 30 - 50
		assertThat(expected).isEqualTo(pen("245.00"));
	}

	@Test
	void anEmptyShiftExpectsExactlyTheFloat() {
		var totals = ShiftTotals.empty(PEN);

		assertThat(ShiftReconciliation.expectedCash(pen("80.00"), totals)).isEqualTo(pen("80.00"));
	}

	@Test
	void theDifferenceIsCountedMinusExpected() {
		assertThat(ShiftReconciliation.difference(pen("240.00"), pen("245.00"))).isEqualTo(pen("-5.00"));
		assertThat(ShiftReconciliation.difference(pen("250.00"), pen("245.00"))).isEqualTo(pen("5.00"));
	}

	@Test
	void aNoteIsNeededOnlyWhenTheDifferenceExceedsTheThresholdInEitherDirection() {
		var threshold = pen("5.00");

		assertThat(ShiftReconciliation.needsNote(pen("5.00"), threshold)).isFalse();
		assertThat(ShiftReconciliation.needsNote(pen("-5.00"), threshold)).isFalse();
		assertThat(ShiftReconciliation.needsNote(pen("5.01"), threshold)).isTrue();
		assertThat(ShiftReconciliation.needsNote(pen("-5.01"), threshold)).isTrue();
		assertThat(ShiftReconciliation.needsNote(Money.zero(PEN), threshold)).isFalse();
	}

	@Test
	void aZeroThresholdDemandsANoteForAnyDifference() {
		assertThat(ShiftReconciliation.needsNote(pen("0.01"), Money.zero(PEN))).isTrue();
		assertThat(ShiftReconciliation.needsNote(Money.zero(PEN), Money.zero(PEN))).isFalse();
	}

	private static Money pen(String amount) {
		return Money.of(amount, PEN);
	}

}
