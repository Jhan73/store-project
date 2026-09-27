package com.jhanantezana.jugueria.store;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

public record OpeningHourSnapshot(DayOfWeek dayOfWeek, boolean closed, @Nullable LocalTime opensAt,
		@Nullable LocalTime closesAt) {
}
