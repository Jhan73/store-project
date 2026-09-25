package com.jhanantezana.archfixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

public class ClockBypass {

	public Object[] readTime() {
		return new Object[] { Instant.now(), LocalDate.now(ZoneId.of("America/Lima")), LocalDateTime.now(), new Date(),
				System.currentTimeMillis() };
	}

	public List<Supplier<?>> readTimeLater() {
		return List.of(Instant::now, Date::new);
	}

}
