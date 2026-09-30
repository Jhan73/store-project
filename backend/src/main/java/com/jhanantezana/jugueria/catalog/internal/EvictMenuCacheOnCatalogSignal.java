package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.RealtimeSignalReceived;

// Runs on every task when a catalog signal arrives over NOTIFY, so no task keeps serving a menu another one changed.
@Component
class EvictMenuCacheOnCatalogSignal {

	private static final String CATALOG_TOPIC = "catalog";

	private final MenuCache menuCache;

	EvictMenuCacheOnCatalogSignal(MenuCache menuCache) {
		this.menuCache = menuCache;
	}

	@EventListener
	void on(RealtimeSignalReceived signal) {
		if (CATALOG_TOPIC.equals(signal.topic())) {
			menuCache.evict();
		}
	}

}
