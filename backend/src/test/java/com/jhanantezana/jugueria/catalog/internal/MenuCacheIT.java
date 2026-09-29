package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.RealtimeSignalReceived;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class MenuCacheIT {

	@Autowired
	MenuCache menuCache;

	@MockitoSpyBean
	MenuQueries queries;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void start() {
		menuCache.evict();
		Mockito.clearInvocations(queries);
	}

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void loadsTheMenuOnceAndServesTheCachedCopyAfterwards() {
		var first = menuCache.menu();
		var second = menuCache.menu();

		assertThat(second).isSameAs(first);
		verify(queries, times(1)).load();
	}

	@Test
	void loadsAgainAfterAnEviction() {
		menuCache.menu();

		menuCache.evict();
		menuCache.menu();

		verify(queries, times(2)).load();
	}

	@Test
	void evictingAfterCommitLeavesTheCacheUntilTheTransactionCommits() {
		menuCache.menu();

		transactions.executeWithoutResult(status -> {
			menuCache.evictAfterCommit();
			menuCache.menu();
			verify(queries, times(1)).load();
		});
		menuCache.menu();

		verify(queries, times(2)).load();
	}

	@Test
	void aRolledBackTransactionNeverEvicts() {
		menuCache.menu();

		transactions.executeWithoutResult(status -> {
			menuCache.evictAfterCommit();
			status.setRollbackOnly();
		});
		menuCache.menu();

		verify(queries, times(1)).load();
	}

	@Test
	void evictsRightAwayWhenNoTransactionIsActive() {
		menuCache.menu();

		menuCache.evictAfterCommit();
		menuCache.menu();

		verify(queries, times(2)).load();
	}

	@Test
	void aCatalogSignalFromAnotherTaskEvictsTheMenu() {
		menuCache.menu();

		events.publishEvent(new RealtimeSignalReceived("catalog", "PRODUCT_AVAILABILITY_CHANGED", Map.of("productId", "x")));
		menuCache.menu();

		verify(queries, times(2)).load();
	}

	@Test
	void aSignalForAnotherTopicLeavesTheMenuCached() {
		menuCache.menu();

		events.publishEvent(new RealtimeSignalReceived("store-status", "STORE_SETTINGS_CHANGED", Map.of()));
		menuCache.menu();

		verify(queries, times(1)).load();
	}

	@Test
	void theCacheExpiresOnItsOwnAfterFiveMinutesAsASafetyNet() {
		var nativeCache = ((CaffeineCache) cacheManager.getCache("menu")).getNativeCache();

		var expiry = nativeCache.policy().expireAfterWrite().orElseThrow();

		assertThat(expiry.getExpiresAfter(TimeUnit.MINUTES)).isEqualTo(5);
	}

}
