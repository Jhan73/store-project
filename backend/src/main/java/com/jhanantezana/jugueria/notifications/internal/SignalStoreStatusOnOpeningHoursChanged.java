package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.store.OpeningHoursChanged;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalStoreStatusOnOpeningHoursChanged {

	private final AppEventsPublisher appEvents;

	SignalStoreStatusOnOpeningHoursChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(OpeningHoursChanged event) {
		appEvents.publish(RealtimeTopic.STORE_STATUS, "OPENING_HOURS_CHANGED",
				Map.of("settingsId", event.settingsId().toString()));
	}

}
