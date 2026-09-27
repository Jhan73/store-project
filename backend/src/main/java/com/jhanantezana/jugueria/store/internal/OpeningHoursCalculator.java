package com.jhanantezana.jugueria.store.internal;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain logic (no Spring, no I/O): whether the store is open at a given instant, store zone, and hours.
final class OpeningHoursCalculator {

	private OpeningHoursCalculator() {
	}

	static boolean isOpenAt(ZoneId zone, List<OpeningHour> hours, Instant instant) {
		var byDay = hours.stream().collect(Collectors.toMap(OpeningHour::getDayOfWeek, Function.identity()));
		var zoned = instant.atZone(zone);
		var time = zoned.toLocalTime();
		var today = byDay.get(zoned.getDayOfWeek());
		if (today != null && !today.isClosed()) {
			if (isNormalSpan(today) && !time.isBefore(today.getOpensAt()) && time.isBefore(today.getClosesAt())) {
				return true;
			}
			if (!isNormalSpan(today) && !time.isBefore(today.getOpensAt())) {
				return true;
			}
		}
		var yesterday = byDay.get(zoned.getDayOfWeek().minus(1));
		return yesterday != null && !yesterday.isClosed() && !isNormalSpan(yesterday)
				&& time.isBefore(yesterday.getClosesAt());
	}

	private static boolean isNormalSpan(OpeningHour hour) {
		return hour.getOpensAt().isBefore(hour.getClosesAt());
	}

}
