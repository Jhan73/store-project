package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class MoneyTest {

	static final Currency PEN = Currency.getInstance("PEN");
	static final Currency USD = Currency.getInstance("USD");

	@Test
	void normalizesTheAmountToTwoDecimals() {
		assertThat(Money.of("12.5", PEN).amount()).isEqualByComparingTo("12.50").hasScaleOf(2);
		assertThat(Money.of("12", PEN)).isEqualTo(Money.of("12.00", PEN));
	}

	@Test
	void rejectsAmountsWithMoreThanTwoDecimals() {
		assertThatIllegalArgumentException().isThrownBy(() -> Money.of("12.505", PEN));
	}

	@Test
	void keepsTrailingZerosBeyondTwoDecimalsWhenTheValueFits() {
		assertThat(Money.of("12.500", PEN).amount()).hasScaleOf(2);
	}

	@Test
	void rejectsMissingParts() {
		assertThatNullPointerException().isThrownBy(() -> new Money(null, PEN));
		assertThatNullPointerException().isThrownBy(() -> new Money(BigDecimal.ONE, null));
	}

	@Test
	void addsAndSubtractsInTheSameCurrency() {
		assertThat(Money.of("10.50", PEN).plus(Money.of("2.25", PEN))).isEqualTo(Money.of("12.75", PEN));
		assertThat(Money.of("10.50", PEN).minus(Money.of("12.00", PEN))).isEqualTo(Money.of("-1.50", PEN));
	}

	@Test
	void multipliesByAQuantity() {
		assertThat(Money.of("4.50", PEN).times(3)).isEqualTo(Money.of("13.50", PEN));
	}

	@Test
	void rejectsMixingCurrencies() {
		assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1", PEN).plus(Money.of("1", USD)));
		assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1", PEN).minus(Money.of("1", USD)));
		assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1", PEN).compareTo(Money.of("1", USD)));
	}

	@Test
	void comparesByAmount() {
		assertThat(Money.of("9.99", PEN)).isLessThan(Money.of("10", PEN));
		assertThat(Money.zero(PEN).isZero()).isTrue();
		assertThat(Money.of("-0.01", PEN).isNegative()).isTrue();
		assertThat(Money.of("0.01", PEN).isNegative()).isFalse();
	}

	@Test
	void serializesTheAmountAsAString() {
		var json = JsonMapper.builder().build();

		assertThat(json.writeValueAsString(Money.of("12.5", PEN)))
			.isEqualTo("{\"amount\":\"12.50\",\"currency\":\"PEN\"}");
		assertThat(json.readValue("{\"amount\":\"12.50\",\"currency\":\"PEN\"}", Money.class))
			.isEqualTo(Money.of("12.50", PEN));
	}

}
