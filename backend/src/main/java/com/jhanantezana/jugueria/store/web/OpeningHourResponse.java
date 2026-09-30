package com.jhanantezana.jugueria.store.web;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.store.internal.OpeningHour;

record OpeningHourResponse(DayOfWeek dayOfWeek, boolean closed, @Nullable LocalTime opensAt,
		@Nullable LocalTime closesAt) {

	static OpeningHourResponse from(OpeningHour hour) {
		return new OpeningHourResponse(hour.getDayOfWeek(), hour.isClosed(), hour.getOpensAt(), hour.getClosesAt());
	}

}
