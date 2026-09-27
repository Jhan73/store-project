package com.jhanantezana.jugueria.store.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class StoreSettingsServiceTest {

	static final Instant NOW = Instant.parse("2026-09-27T09:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Mock
	StoreSettingsRepository repository;

	@Mock
	ApplicationEventPublisher events;

	@Mock
	CurrentActor currentActor;

	@Mock
	NotificationsApi notifications;

	StoreSettingsService service;

	StoreSettings existing;

	@BeforeEach
	void setUp() {
		existing = new StoreSettings("America/Lima", PEN, 10, 2, 15, 5, 10, Money.of("20.00", PEN), 3, 20, NOW);
		when(repository.findAll()).thenReturn(List.of(existing));
		service = new StoreSettingsService(repository, events, currentActor, notifications,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void signalsStoreStatusAfterUpdatingSettings() {
		service.update("America/Lima", PEN, 12, 3, 20, 6, 12, Money.of("30.00", PEN), 4, 25, 0);

		verify(notifications).publish(RealtimeTopic.STORE_STATUS, "STORE_SETTINGS_CHANGED",
				Map.of("settingsId", existing.getId().toString()));
		verify(events).publishEvent(any(Object.class));
	}

}
