package com.jhanantezana.jugueria.store.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Money;

@ExtendWith(MockitoExtension.class)
class OpeningHoursServiceTest {

	static final Instant NOW = Instant.parse("2026-09-27T09:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Mock
	OpeningHourRepository hours;

	@Mock
	StoreSettingsRepository settingsRepository;

	@Mock
	ApplicationEventPublisher events;

	@Mock
	CurrentActor currentActor;

	OpeningHoursService service;

	StoreSettings settingsRow;

	@BeforeEach
	void setUp() {
		settingsRow = new StoreSettings("America/Lima", PEN, 10, 2, 15, 5, 10, Money.of("20.00", PEN), 3, 20, NOW);
		when(settingsRepository.findAll()).thenReturn(List.of(settingsRow));
		when(hours.findAll()).thenReturn(aWeek());
		when(settingsRepository.bumpOpeningHoursVersion(settingsRow.getId(), 0)).thenReturn(1);
		service = new OpeningHoursService(hours, settingsRepository, events, currentActor,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void publishesAnEventAfterReplacingOpeningHours() {
		var updates = Arrays.stream(DayOfWeek.values())
			.map(day -> new OpeningHourUpdate(day, false, LocalTime.of(8, 0), LocalTime.of(20, 0)))
			.toList();

		service.replaceAll(updates, 0);

		verify(events).publishEvent(any(Object.class));
	}

	private static List<OpeningHour> aWeek() {
		return Arrays.stream(DayOfWeek.values())
			.map(day -> new OpeningHour(day, false, LocalTime.of(9, 0), LocalTime.of(18, 0)))
			.toList();
	}

}
