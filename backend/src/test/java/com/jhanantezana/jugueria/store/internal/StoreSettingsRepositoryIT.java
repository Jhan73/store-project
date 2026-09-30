package com.jhanantezana.jugueria.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StoreSettingsRepositoryIT {

	@Autowired
	StoreSettingsRepository settings;

	// Neither the repository nor the entity has a transaction of its own; standalone calls need one.
	@Autowired
	TransactionTemplate transactionTemplate;

	private int originalBasePrepMinutes;

	@AfterEach
	void restoreBasePrepMinutes() {
		transactionTemplate.executeWithoutResult(status -> {
			var current = settings.findAll().get(0);
			current.update(current.getTimeZone(), current.getCurrency(), originalBasePrepMinutes,
					current.getQueueMinutesPerOrder(), current.getBusyModeMinutes(), current.getBoardWarningMinutes(),
					current.getBoardLateMinutes(), current.getRegisterDifferenceThreshold(),
					current.getExceptionThreshold(), current.getOnlineCapacityLimit(), Instant.now());
		});
	}

	// A settings entity loaded before the bump must not revert opening_hours_version on its own later flush.
	@Test
	void aConcurrentOpeningHoursBumpSurvivesAnUnrelatedSettingsFlush() throws InterruptedException {
		var settingsId = settings.findAll().get(0).getId();
		originalBasePrepMinutes = settings.findAll().get(0).getBasePrepMinutes();
		var settingsLoaded = new CountDownLatch(1);
		var hoursBumped = new CountDownLatch(1);
		var loadedVersion = new AtomicLong();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			var settingsFlush = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				var current = settings.findAll().get(0);
				loadedVersion.set(current.getOpeningHoursVersion());
				settingsLoaded.countDown();
				await(hoursBumped);
				current.update(current.getTimeZone(), current.getCurrency(), current.getBasePrepMinutes() + 1,
						current.getQueueMinutesPerOrder(), current.getBusyModeMinutes(), current.getBoardWarningMinutes(),
						current.getBoardLateMinutes(), current.getRegisterDifferenceThreshold(),
						current.getExceptionThreshold(), current.getOnlineCapacityLimit(), Instant.now());
			}));
			var hoursBump = pool.submit(() -> {
				await(settingsLoaded);
				transactionTemplate
					.executeWithoutResult(status -> settings.bumpOpeningHoursVersion(settingsId, loadedVersion.get()));
				hoursBumped.countDown();
			});
			settingsFlush.get(10, TimeUnit.SECONDS);
			hoursBump.get(10, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new AssertionError(e);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(settings.findAll().get(0).getOpeningHoursVersion()).isEqualTo(loadedVersion.get() + 1);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

}
