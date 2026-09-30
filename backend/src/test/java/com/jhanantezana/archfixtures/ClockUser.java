package com.jhanantezana.archfixtures;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class ClockUser {

	public Object[] readTime(Clock clock) {
		return new Object[] { Instant.now(clock), LocalDate.now(clock.withZone(ZoneId.of("America/Lima"))),
				System.nanoTime() };
	}

}
