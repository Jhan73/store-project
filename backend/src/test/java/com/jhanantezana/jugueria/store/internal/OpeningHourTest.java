package com.jhanantezana.jugueria.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.store.StoreError;

class OpeningHourTest {

	@Test
	void createsAnOpenDay() {
		var hour = new OpeningHour(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(22, 0));

		assertThat(hour.isClosed()).isFalse();
		assertThat(hour.getOpensAt()).isEqualTo(LocalTime.of(8, 0));
	}

	@Test
	void createsAClosedDayWithNoTimes() {
		var hour = new OpeningHour(DayOfWeek.SUNDAY, true, null, null);

		assertThat(hour.isClosed()).isTrue();
		assertThat(hour.getOpensAt()).isNull();
		assertThat(hour.getClosesAt()).isNull();
	}

	@Test
	void rejectsAnOpenDayMissingTimes() {
		assertThatThrownBy(() -> new OpeningHour(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), null))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_OPENING_HOURS);
	}

	@Test
	void rejectsEqualOpenAndCloseTimes() {
		assertThatThrownBy(
				() -> new OpeningHour(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(8, 0)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_OPENING_HOURS);
	}

	@Test
	void closeClearsTimes() {
		var hour = new OpeningHour(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(22, 0));

		hour.change(true, null, null);

		assertThat(hour.isClosed()).isTrue();
		assertThat(hour.getOpensAt()).isNull();
	}

	@Test
	void allowsAnOvernightSpan() {
		var hour = new OpeningHour(DayOfWeek.FRIDAY, false, LocalTime.of(18, 0), LocalTime.of(2, 0));

		assertThat(hour.getOpensAt()).isEqualTo(LocalTime.of(18, 0));
		assertThat(hour.getClosesAt()).isEqualTo(LocalTime.of(2, 0));
	}

}
