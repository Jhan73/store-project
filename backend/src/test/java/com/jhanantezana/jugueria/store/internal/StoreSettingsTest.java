package com.jhanantezana.jugueria.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.StoreError;

class StoreSettingsTest {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void createsWithValidValues() {
		var settings = aSettings();

		assertThat(settings.getTimeZone()).isEqualTo("America/Lima");
		assertThat(settings.getBoardWarningMinutes()).isEqualTo(5);
		assertThat(settings.getBoardLateMinutes()).isEqualTo(10);
	}

	@Test
	void rejectsAnInvalidTimeZone() {
		assertThatThrownBy(() -> new StoreSettings("Not/AZone", PEN, 10, 2, 15, 5, 10, Money.of("20.00", PEN), 3, 20,
				NOW)).isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_SETTINGS_VALUE);
	}

	@Test
	void rejectsBoardWarningNotBeforeLate() {
		assertThatThrownBy(() -> new StoreSettings("America/Lima", PEN, 10, 2, 15, 10, 10, Money.of("20.00", PEN), 3,
				20, NOW)).isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_BOARD_THRESHOLDS);
	}

	@Test
	void rejectsANonPositiveBasePrepMinutes() {
		assertThatThrownBy(() -> new StoreSettings("America/Lima", PEN, 0, 2, 15, 5, 10, Money.of("20.00", PEN), 3, 20,
				NOW)).isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_SETTINGS_VALUE);
	}

	@Test
	void rejectsANegativeRegisterDifferenceThreshold() {
		var negative = new Money(java.math.BigDecimal.valueOf(-1), PEN);
		assertThatThrownBy(
				() -> new StoreSettings("America/Lima", PEN, 10, 2, 15, 5, 10, negative, 3, 20, NOW))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_SETTINGS_VALUE);
	}

	@Test
	void rejectsARegisterDifferenceThresholdInAnotherCurrency() {
		var otherCurrency = new Money(java.math.BigDecimal.valueOf(20), Currency.getInstance("USD"));
		assertThatThrownBy(
				() -> new StoreSettings("America/Lima", PEN, 10, 2, 15, 5, 10, otherCurrency, 3, 20, NOW))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_SETTINGS_VALUE);
	}

	@Test
	void updateAppliesNewValuesAndStampsUpdatedAt() {
		var settings = aSettings();
		var later = NOW.plusSeconds(60);

		settings.update("America/Lima", PEN, 12, 3, 20, 6, 12, Money.of("30.00", PEN), 4, 25, later);

		assertThat(settings.getBasePrepMinutes()).isEqualTo(12);
		assertThat(settings.getExceptionThreshold()).isEqualTo(4);
		assertThat(settings.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void updateRejectsInvalidValuesWithoutMutatingTheEntity() {
		var settings = aSettings();

		assertThatThrownBy(() -> settings.update("America/Lima", PEN, 10, 2, 15, 10, 10, Money.of("20.00", PEN), 3, 20,
				NOW.plusSeconds(60))).isInstanceOf(BusinessException.class);
		assertThat(settings.getBoardWarningMinutes()).isEqualTo(5);
		assertThat(settings.getBoardLateMinutes()).isEqualTo(10);
	}

	private static StoreSettings aSettings() {
		return new StoreSettings("America/Lima", PEN, 10, 2, 15, 5, 10, Money.of("20.00", PEN), 3, 20, NOW);
	}

}
