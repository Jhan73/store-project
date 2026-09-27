package com.jhanantezana.jugueria.store.internal;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

public record OpeningHourUpdate(DayOfWeek dayOfWeek, boolean closed, @Nullable LocalTime opensAt,
		@Nullable LocalTime closesAt) {
}
