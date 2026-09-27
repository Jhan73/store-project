package com.jhanantezana.jugueria.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class OpeningHoursCalculatorTest {

	static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Test
	void isOpenDuringARegularWindow() {
		var hours = weekOf(new OpeningHour(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(22, 0)));
		// 2026-09-28 is a Monday, 15:00 Lima time (UTC-5) -> 20:00Z.
		var instant = Instant.parse("2026-09-28T20:00:00Z");

		assertThat(OpeningHoursCalculator.isOpenAt(LIMA, hours, instant)).isTrue();
	}

	@Test
	void isClosedBeforeOpening() {
		var hours = weekOf(new OpeningHour(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(22, 0)));
		// 05:00 Lima time -> 10:00Z, before the 08:00 opening.
		var instant = Instant.parse("2026-09-28T10:00:00Z");

		assertThat(OpeningHoursCalculator.isOpenAt(LIMA, hours, instant)).isFalse();
	}

	@Test
	void isClosedOnADayMarkedClosed() {
		var hours = weekOf(new OpeningHour(DayOfWeek.MONDAY, true, null, null));
		var instant = Instant.parse("2026-09-28T20:00:00Z");

		assertThat(OpeningHoursCalculator.isOpenAt(LIMA, hours, instant)).isFalse();
	}

	@Test
	void isOpenPastMidnightOnAnOvernightSpan() {
		// Friday 18:00 to Saturday 02:00.
		var hours = weekOf(new OpeningHour(DayOfWeek.FRIDAY, false, LocalTime.of(18, 0), LocalTime.of(2, 0)),
				new OpeningHour(DayOfWeek.SATURDAY, true, null, null));
		// 2026-10-03 is a Saturday, 01:00 Lima time -> 06:00Z: still within Friday's overnight span.
		var instant = Instant.parse("2026-10-03T06:00:00Z");

		assertThat(OpeningHoursCalculator.isOpenAt(LIMA, hours, instant)).isTrue();
	}

	@Test
	void isClosedAfterAnOvernightSpanEnds() {
		var hours = weekOf(new OpeningHour(DayOfWeek.FRIDAY, false, LocalTime.of(18, 0), LocalTime.of(2, 0)),
				new OpeningHour(DayOfWeek.SATURDAY, true, null, null));
		// 03:00 Lima time Saturday -> 08:00Z: after the 02:00 close.
		var instant = Instant.parse("2026-10-03T08:00:00Z");

		assertThat(OpeningHoursCalculator.isOpenAt(LIMA, hours, instant)).isFalse();
	}

	private static List<OpeningHour> weekOf(OpeningHour... specified) {
		var byDay = new java.util.HashMap<DayOfWeek, OpeningHour>();
		for (var day : DayOfWeek.values()) {
			byDay.put(day, new OpeningHour(day, true, null, null));
		}
		for (var hour : specified) {
			byDay.put(hour.getDayOfWeek(), hour);
		}
		return List.copyOf(byDay.values());
	}

}
