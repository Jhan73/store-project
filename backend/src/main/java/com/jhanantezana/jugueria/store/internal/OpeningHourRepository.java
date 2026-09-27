package com.jhanantezana.jugueria.store.internal;

import java.time.DayOfWeek;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, UUID> {

	// day_of_week is stored as its name (EnumType.STRING), so sorting must happen in Java, not SQL.
	OpeningHour findByDayOfWeek(DayOfWeek dayOfWeek);

}
