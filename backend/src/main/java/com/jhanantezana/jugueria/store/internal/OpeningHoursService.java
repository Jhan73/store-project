package com.jhanantezana.jugueria.store.internal;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.OpeningHoursChanged;
import com.jhanantezana.jugueria.store.StoreError;

@Service
public class OpeningHoursService {

	private final OpeningHourRepository hours;

	private final StoreSettingsRepository settings;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	OpeningHoursService(OpeningHourRepository hours, StoreSettingsRepository settings,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.hours = hours;
		this.settings = settings;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<OpeningHour> list() {
		return sorted(hours.findAll());
	}

	// Replaces the whole week atomically: the 7 rows are seeded once by migration and never inserted or
	// deleted here, so this always mutates existing rows.
	@Transactional
	public List<OpeningHour> replaceAll(List<OpeningHourUpdate> updates) {
		var byDay = updates.stream()
			.collect(Collectors.toMap(OpeningHourUpdate::dayOfWeek, Function.identity(), (a, b) -> b,
					() -> new EnumMap<DayOfWeek, OpeningHourUpdate>(DayOfWeek.class)));
		if (byDay.size() != DayOfWeek.values().length) {
			throw new BusinessException(StoreError.INVALID_OPENING_HOURS,
					"Exactly one entry per day of the week is required");
		}
		var current = sorted(hours.findAll());
		var before = current.stream().map(OpeningHour::snapshot).toList();
		for (var hour : current) {
			var update = byDay.get(hour.getDayOfWeek());
			hour.change(update.closed(), update.opensAt(), update.closesAt());
		}
		var after = sorted(hours.findAll()).stream().map(OpeningHour::snapshot).toList();
		var now = Instant.now(clock);
		var settingsId = settings.findAll().getFirst().getId();
		events.publishEvent(
				new OpeningHoursChanged(settingsId, before, after, currentActor.id(), currentActor.role(), now));
		return sorted(hours.findAll());
	}

	private static List<OpeningHour> sorted(List<OpeningHour> hours) {
		return hours.stream().sorted(Comparator.comparing(OpeningHour::getDayOfWeek)).toList();
	}

}
