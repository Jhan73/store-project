package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalCatalogOnCategoryChanged {

	private final AppEventsPublisher appEvents;

	SignalCatalogOnCategoryChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(CategoryChanged event) {
		appEvents.publish(RealtimeTopic.CATALOG, "CATEGORY_CHANGED",
				Map.of("categoryId", event.categoryId().toString()));
	}

}
