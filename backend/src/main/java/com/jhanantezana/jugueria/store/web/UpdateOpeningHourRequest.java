package com.jhanantezana.jugueria.store.web;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.store.internal.OpeningHourUpdate;

import jakarta.validation.constraints.NotNull;

record UpdateOpeningHourRequest(@NotNull DayOfWeek dayOfWeek, boolean closed, @Nullable LocalTime opensAt,
		@Nullable LocalTime closesAt) {

	OpeningHourUpdate toUpdate() {
		return new OpeningHourUpdate(dayOfWeek, closed, opensAt, closesAt);
	}

}
