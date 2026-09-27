package com.jhanantezana.jugueria.store.internal;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.Singletons;
import com.jhanantezana.jugueria.store.OpeningHoursChanged;
import com.jhanantezana.jugueria.store.StoreError;

@Service
public class OpeningHoursService {

	// Carries the week alongside the settings row's opening-hours counter, which the ETag is derived from.
	public record OpeningHoursResult(List<OpeningHour> hours, long version) {
	}

	private final OpeningHourRepository hours;

	private final StoreSettingsRepository settings;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final NotificationsApi notifications;

	private final Clock clock;

	OpeningHoursService(OpeningHourRepository hours, StoreSettingsRepository settings,
			ApplicationEventPublisher events, CurrentActor currentActor, NotificationsApi notifications,
			Clock clock) {
		this.hours = hours;
		this.settings = settings;
		this.events = events;
		this.currentActor = currentActor;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public OpeningHoursResult list() {
		return new OpeningHoursResult(sorted(hours.findAll()), currentSettings().getOpeningHoursVersion());
	}

	// Replaces the whole week atomically; the 7 rows are seeded once by migration and never inserted or deleted here.
	@Transactional
	public OpeningHoursResult replaceAll(List<OpeningHourUpdate> updates, long expectedVersion) {
		var byDay = distinctByDay(updates);
		var settingsRow = currentSettings();
		if (settingsRow.getOpeningHoursVersion() != expectedVersion) {
			throw preconditionFailed(settingsRow.getOpeningHoursVersion());
		}
		var current = sorted(hours.findAll());
		var before = current.stream().map(OpeningHour::snapshot).toList();
		for (var hour : current) {
			var update = byDay.get(hour.getDayOfWeek());
			hour.change(update.closed(), update.opensAt(), update.closesAt());
		}
		var after = sorted(hours.findAll()).stream().map(OpeningHour::snapshot).toList();
		var now = Instant.now(clock);
		var updatedRows = settings.bumpOpeningHoursVersion(settingsRow.getId(), expectedVersion);
		if (updatedRows == 0) {
			throw new BusinessException(CommonError.CONCURRENT_MODIFICATION, "Opening hours were changed concurrently");
		}
		events.publishEvent(
				new OpeningHoursChanged(settingsRow.getId(), before, after, currentActor.id(), currentActor.role(), now));
		// Same transaction as the change above: NOTIFY only reaches other instances once it commits.
		notifications.publish(RealtimeTopic.STORE_STATUS, "OPENING_HOURS_CHANGED",
				Map.of("settingsId", settingsRow.getId().toString()));
		return new OpeningHoursResult(sorted(hours.findAll()), expectedVersion + 1);
	}

	private StoreSettings currentSettings() {
		return Singletons.requireOne(settings.findAll());
	}

	private static Map<DayOfWeek, OpeningHourUpdate> distinctByDay(List<OpeningHourUpdate> updates) {
		var byDay = new EnumMap<DayOfWeek, OpeningHourUpdate>(DayOfWeek.class);
		for (var update : updates) {
			if (byDay.putIfAbsent(update.dayOfWeek(), update) != null) {
				throw new BusinessException(StoreError.INVALID_OPENING_HOURS, "Each day of the week must appear once");
			}
		}
		if (byDay.size() != DayOfWeek.values().length) {
			throw new BusinessException(StoreError.INVALID_OPENING_HOURS,
					"Exactly one entry per day of the week is required");
		}
		return byDay;
	}

	private static List<OpeningHour> sorted(List<OpeningHour> hours) {
		return hours.stream().sorted(Comparator.comparing(OpeningHour::getDayOfWeek)).toList();
	}

	private static BusinessException preconditionFailed(long currentVersion) {
		return new BusinessException(CommonError.PRECONDITION_FAILED, "If-Match does not match the current ETag",
				Map.of("currentETag", ETags.format(currentVersion)));
	}

}
