package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;

// Runs after the publisher's own transaction commits, on its own thread and its own transaction.
// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalStoreStatusOnStoreSettingsChanged {

	private final AppEventsPublisher appEvents;

	SignalStoreStatusOnStoreSettingsChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	// No transaction is active at this point (the publisher's already committed); always start a fresh one.
	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener
	void on(StoreSettingsChanged event) {
		appEvents.publish(RealtimeTopic.STORE_STATUS, "STORE_SETTINGS_CHANGED",
				Map.of("settingsId", event.settingsId().toString()));
	}

}
