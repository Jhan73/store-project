package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalStoreStatusOnStoreSettingsChanged {

	private final AppEventsPublisher appEvents;

	SignalStoreStatusOnStoreSettingsChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(StoreSettingsChanged event) {
		appEvents.publish(RealtimeTopic.STORE_STATUS, "STORE_SETTINGS_CHANGED",
				Map.of("settingsId", event.settingsId().toString()));
	}

}
