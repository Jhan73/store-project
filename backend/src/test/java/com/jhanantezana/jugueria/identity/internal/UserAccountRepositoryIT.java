package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class UserAccountRepositoryIT {

	@Autowired
	UserAccountRepository accounts;

	@AfterEach
	void cleanUp() {
		accounts.deleteAll();
	}

	@Test
	void roundTripsAnAccountAndFindsItByEmailIgnoringCase() {
		var saved = accounts.save(new UserAccount("Cashier@Jugueria.pe", "hash", Role.CASHIER));

		var found = accounts.findByEmailIgnoreCase("cashier@jugueria.pe").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void rejectsTwoAccountsWithTheSameEmailRegardlessOfCase() {
		accounts.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> accounts.saveAndFlush(new UserAccount("CASHIER@jugueria.pe", "hash", Role.CASHIER)));
	}

	@Test
	void accumulatesConcurrentFailedAttemptsWithoutLosingAnUpdate() throws InterruptedException {
		var account = accounts.save(new UserAccount("racer@jugueria.pe", "hash", Role.CASHIER));
		var attempts = 8;
		var ready = new CountDownLatch(attempts);
		var go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		try {
			for (int i = 0; i < attempts; i++) {
				pool.submit(() -> {
					ready.countDown();
					await(go);
					accounts.registerFailedAttempt(account.getId(), 100, null);
				});
			}
			ready.await();
			go.countDown();
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(accounts.findById(account.getId()).orElseThrow().getFailedAttempts()).isEqualTo(attempts);
	}

	@Test
	void locksTheAccountOnceItReachesTheMaxAttempts() {
		var account = accounts.save(new UserAccount("locked@jugueria.pe", "hash", Role.CASHIER));
		var lockedUntil = Instant.parse("2026-09-25T12:15:00Z");

		accounts.registerFailedAttempt(account.getId(), 1, lockedUntil);

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
		assertThat(reloaded.getLockedUntil()).isEqualTo(lockedUntil);
	}

	@Test
	void resetsFailedAttemptsAndTheLock() {
		var account = accounts.save(new UserAccount("reset@jugueria.pe", "hash", Role.CASHIER));
		accounts.registerFailedAttempt(account.getId(), 1, Instant.parse("2026-09-25T12:15:00Z"));

		accounts.resetFailedAttempts(account.getId());

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isZero();
		assertThat(reloaded.getLockedUntil()).isNull();
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
