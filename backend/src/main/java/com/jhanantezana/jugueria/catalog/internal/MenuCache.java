package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// In process, per task. Other tasks are told through the NOTIFY bridge; the cache's own expiry is the safety net.
@Component
public class MenuCache {

	static final String CACHE_NAME = "menu";

	private static final String KEY = "menu";

	private final Cache cache;

	private final MenuQueries queries;

	MenuCache(CacheManager cacheManager, MenuQueries queries) {
		this.cache = cacheManager.getCache(CACHE_NAME);
		this.queries = queries;
	}

	public MenuView menu() {
		return cache.get(KEY, queries::load);
	}

	void evict() {
		cache.evict(KEY);
	}

	// A writer's own transaction must be visible before the cache can refill, so eviction waits for the commit.
	void evictAfterCommit() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			evict();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				evict();
			}

		});
	}

}
