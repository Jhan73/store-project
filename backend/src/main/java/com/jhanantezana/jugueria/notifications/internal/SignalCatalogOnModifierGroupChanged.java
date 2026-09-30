package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalCatalogOnModifierGroupChanged {

	private final AppEventsPublisher appEvents;

	SignalCatalogOnModifierGroupChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(ModifierGroupChanged event) {
		appEvents.publish(RealtimeTopic.CATALOG, "MODIFIER_GROUP_CHANGED",
				Map.of("groupId", event.groupId().toString()));
	}

}
