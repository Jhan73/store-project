package com.jhanantezana.jugueria.reporting.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A moment as the store lived it: calendar day, hour and ISO weekday (Monday 1 to Sunday 7) in the store zone. */
record BusinessMoment(LocalDate date, int hour, int weekday) {

	static BusinessMoment of(Instant instant, ZoneId zone) {
		var local = instant.atZone(zone);
		return new BusinessMoment(local.toLocalDate(), local.getHour(), local.getDayOfWeek().getValue());
	}

}
