package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class UserAccountRepositoryIT {

	static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

	@Autowired
	UserAccountRepository accounts;

	// registerFailedAttempt/resetFailedAttempts have no @Transactional of their own (that belongs only
	// on LoginService); a standalone call from this test needs its own transaction.
	@Autowired
	TransactionTemplate transactionTemplate;

	@AfterEach
	void cleanUp() {
		accounts.deleteAll();
	}

	@Test
	void roundTripsAnAccountAndFindsItByEmailIgnoringCase() {
		var saved = accounts.save(new UserAccount("Cashier@Jugueria.pe", "hash", Role.CASHIER, NOW));

		var found = accounts.findByEmailIgnoreCase("cashier@jugueria.pe").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getCreatedAt()).isEqualTo(NOW);
		assertThat(found.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsTwoAccountsWithTheSameEmailRegardlessOfCase() {
		accounts.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW));

		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(
				() -> accounts.saveAndFlush(new UserAccount("CASHIER@jugueria.pe", "hash", Role.CASHIER, NOW)));
	}

	@Test
	void accumulatesConcurrentFailedAttemptsWithoutLosingAnUpdate() throws InterruptedException {
		var account = accounts.save(new UserAccount("racer@jugueria.pe", "hash", Role.CASHIER, NOW));
		var attempts = 8;
		var ready = new CountDownLatch(attempts);
		var go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		try {
			for (int i = 0; i < attempts; i++) {
				pool.submit(() -> {
					ready.countDown();
					await(go);
					registerFailedAttempt(account.getId(), NOW, 100, null);
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
		var account = accounts.save(new UserAccount("locked@jugueria.pe", "hash", Role.CASHIER, NOW));
		var lockedUntil = NOW.plus(Duration.ofMinutes(15));

		registerFailedAttempt(account.getId(), NOW, 1, lockedUntil);

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
		assertThat(reloaded.getLockedUntil()).isEqualTo(lockedUntil);
		assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	void resetsFailedAttemptsAndTheLock() {
		var account = accounts.save(new UserAccount("reset@jugueria.pe", "hash", Role.CASHIER, NOW));
		registerFailedAttempt(account.getId(), NOW, 1, NOW.plus(Duration.ofMinutes(15)));

		var resetAt = NOW.plus(Duration.ofMinutes(20));
		resetFailedAttempts(account.getId(), resetAt);

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isZero();
		assertThat(reloaded.getLockedUntil()).isNull();
		assertThat(reloaded.getUpdatedAt()).isEqualTo(resetAt);
	}

	@Test
	void restartsTheCountAtOneOnceThePreviousLockHasExpired() {
		var lockedInThePast = NOW.minus(Duration.ofMinutes(1));
		var account = accounts.save(new UserAccount("expired@jugueria.pe", "hash", Role.CASHIER, NOW, 5, lockedInThePast));

		registerFailedAttempt(account.getId(), NOW, 5, NOW.plus(Duration.ofMinutes(15)));

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
		assertThat(reloaded.getLockedUntil()).isNull();
	}

	@Test
	void locksAgainImmediatelyWhenMaxAttemptsIsOneAndThePreviousLockHasExpired() {
		var lockedInThePast = NOW.minus(Duration.ofMinutes(1));
		var account = accounts.save(new UserAccount("relocked@jugueria.pe", "hash", Role.CASHIER, NOW, 5, lockedInThePast));
		var newLockedUntil = NOW.plus(Duration.ofMinutes(15));

		registerFailedAttempt(account.getId(), NOW, 1, newLockedUntil);

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
		assertThat(reloaded.getLockedUntil()).isEqualTo(newLockedUntil);
	}

	private void registerFailedAttempt(UUID id, Instant now, int maxAttempts, Instant lockUntil) {
		transactionTemplate
			.executeWithoutResult(status -> accounts.registerFailedAttempt(id, now, maxAttempts, lockUntil));
	}

	private void resetFailedAttempts(UUID id, Instant now) {
		transactionTemplate.executeWithoutResult(status -> accounts.resetFailedAttempts(id, now));
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
