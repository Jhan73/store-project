package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// The one door every catalog command publishes its change through, so no command can skip the menu eviction.
@Component
class CatalogChanges {

	private final ApplicationEventPublisher events;

	private final MenuCache menuCache;

	CatalogChanges(ApplicationEventPublisher events, MenuCache menuCache) {
		this.events = events;
		this.menuCache = menuCache;
	}

	void publish(Object event) {
		events.publishEvent(event);
		menuCache.evictAfterCommit();
	}

}
