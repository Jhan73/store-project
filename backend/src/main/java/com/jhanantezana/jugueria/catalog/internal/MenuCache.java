package com.jhanantezana.jugueria.catalog.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.json.JsonMapper;

// In process, per task. Other tasks are told through the NOTIFY bridge; the cache's own expiry is the safety net.
@Component
public class MenuCache {

	static final String CACHE_NAME = "menu";

	private static final String KEY = "menu";

	private final Cache cache;

	private final MenuQueries queries;

	private final JsonMapper mapper;

	MenuCache(CacheManager cacheManager, MenuQueries queries, JsonMapper mapper) {
		this.cache = cacheManager.getCache(CACHE_NAME);
		this.queries = queries;
		this.mapper = mapper;
	}

	public MenuSnapshot menu() {
		return cache.get(KEY, this::load);
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

	private MenuSnapshot load() {
		var menu = queries.load();
		return new MenuSnapshot(menu, etagOf(menu));
	}

	private String etagOf(MenuView menu) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(menu));
			return HexFormat.of().formatHex(digest, 0, 16);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", e);
		}
	}

}
