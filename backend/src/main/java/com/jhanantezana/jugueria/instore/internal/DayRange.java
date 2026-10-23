package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;

/** Store-day filters turned into the half-open instant range they cover: {@code from} inclusive, {@code to} exclusive. */
record DayRange(@Nullable Instant from, @Nullable Instant to) {

	static DayRange of(@Nullable LocalDate firstDay, @Nullable LocalDate lastDay, ZoneId zone) {
		if (firstDay != null && lastDay != null && lastDay.isBefore(firstDay)) {
			throw new BusinessException(InstoreError.INVALID_DATE_RANGE, "The last day is before the first day");
		}
		return new DayRange(firstDay == null ? null : firstDay.atStartOfDay(zone).toInstant(),
				lastDay == null ? null : lastDay.plusDays(1).atStartOfDay(zone).toInstant());
	}

}
