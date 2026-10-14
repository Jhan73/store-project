package com.jhanantezana.jugueria.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class BusinessMomentTest {

	static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Test
	void readsTheDayHourAndWeekdayInTheStoreZoneNotInUtc() {
		var moment = BusinessMoment.of(Instant.parse("2026-10-13T02:30:00Z"), LIMA);

		assertThat(moment.date()).isEqualTo(LocalDate.parse("2026-10-12"));
		assertThat(moment.hour()).isEqualTo(21);
		assertThat(moment.weekday()).isEqualTo(1);
	}

	@Test
	void numbersSundayAsSevenAndMondayAsOne() {
		assertThat(BusinessMoment.of(Instant.parse("2026-10-18T15:00:00Z"), LIMA).weekday()).isEqualTo(7);
		assertThat(BusinessMoment.of(Instant.parse("2026-10-19T15:00:00Z"), LIMA).weekday()).isEqualTo(1);
	}

}
