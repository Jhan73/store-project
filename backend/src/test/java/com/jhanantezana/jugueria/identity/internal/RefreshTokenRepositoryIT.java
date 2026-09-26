package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RefreshTokenRepositoryIT {

	static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

	// Far enough in the past that family_started_at > NO_ABSOLUTE_FLOOR always holds.
	static final Instant NO_ABSOLUTE_FLOOR = Instant.EPOCH;

	@Autowired
	RefreshTokenRepository tokens;

	@Autowired
	UserAccountRepository accounts;

	// Neither repository has a transaction of its own; standalone calls need one.
	@Autowired
	TransactionTemplate transactionTemplate;

	@AfterEach
	void cleanUp() {
		tokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void roundTripsATokenAndFindsItByHash() {
		var user = accounts.save(new UserAccount("racer@jugueria.pe", "hash", Role.CASHIER, NOW));
		var familyId = UUID.randomUUID();
		var saved = tokens
			.save(new RefreshToken(familyId, user.getId(), "hash-value", NOW, NOW.plus(Duration.ofDays(7)), NOW));

		var found = tokens.findByTokenHash("hash-value").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getFamilyId()).isEqualTo(familyId);
		assertThat(found.getUserId()).isEqualTo(user.getId());
		assertThat(found.getFamilyStartedAt()).isEqualTo(NOW);
		assertThat(found.getUsedAt()).isNull();
		assertThat(found.getRevokedAt()).isNull();
	}

	@Test
	void marksATokenUsedOnlyOnce() {
		var user = accounts.save(new UserAccount("once@jugueria.pe", "hash", Role.CASHIER, NOW));
		var token = tokens
			.save(new RefreshToken(UUID.randomUUID(), user.getId(), "once", NOW, NOW.plus(Duration.ofDays(7)), NOW));

		var firstAttempt = markUsed(token.getId(), NOW);
		var secondAttempt = markUsed(token.getId(), NOW.plusSeconds(1));

		assertThat(firstAttempt).isEqualTo(1);
		assertThat(secondAttempt).isEqualTo(0);
	}

	@Test
	void doesNotMarkAnExpiredOrRevokedTokenAsUsed() {
		var user = accounts.save(new UserAccount("expired@jugueria.pe", "hash", Role.CASHIER, NOW));
		var expired = tokens
			.save(new RefreshToken(UUID.randomUUID(), user.getId(), "expired", NOW, NOW.minusSeconds(1), NOW));
		var revokedFamily = UUID.randomUUID();
		var revoked = tokens
			.save(new RefreshToken(revokedFamily, user.getId(), "revoked", NOW, NOW.plus(Duration.ofDays(7)), NOW));
		revokeFamily(revokedFamily, NOW);

		assertThat(markUsed(expired.getId(), NOW)).isZero();
		assertThat(markUsed(revoked.getId(), NOW)).isZero();
	}

	@Test
	void doesNotMarkATokenUsedOnceItsFamilyPassedTheAbsoluteFloor() {
		var user = accounts.save(new UserAccount("stale-family@jugueria.pe", "hash", Role.CASHIER, NOW));
		var familyStartedAt = NOW.minus(Duration.ofDays(31));
		var token = tokens.save(new RefreshToken(UUID.randomUUID(), user.getId(), "stale", NOW,
				NOW.plus(Duration.ofDays(1)), familyStartedAt));
		var absoluteFloor = NOW.minus(Duration.ofDays(30));

		var affected = transactionTemplate.execute(status -> tokens.markUsed(token.getId(), NOW, absoluteFloor));

		assertThat(affected).isZero();
	}

	@Test
	void revokesEveryTokenInAFamilyButLeavesOtherFamiliesAlone() {
		var user = accounts.save(new UserAccount("family@jugueria.pe", "hash", Role.CASHIER, NOW));
		var familyId = UUID.randomUUID();
		var first = tokens
			.save(new RefreshToken(familyId, user.getId(), "first", NOW, NOW.plus(Duration.ofDays(7)), NOW));
		var rotated = tokens
			.save(new RefreshToken(familyId, user.getId(), "rotated", NOW, NOW.plus(Duration.ofDays(7)), NOW));
		var otherFamily = tokens
			.save(new RefreshToken(UUID.randomUUID(), user.getId(), "other", NOW, NOW.plus(Duration.ofDays(7)), NOW));

		var affected = revokeFamily(familyId, NOW);

		assertThat(affected).isEqualTo(2);
		assertThat(tokens.findById(first.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(tokens.findById(rotated.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(tokens.findById(otherFamily.getId()).orElseThrow().getRevokedAt()).isNull();
	}

	@Test
	void revokesEveryTokenOfAUserAcrossFamilies() {
		var user = accounts.save(new UserAccount("multi@jugueria.pe", "hash", Role.CASHIER, NOW));
		var otherUser = accounts.save(new UserAccount("other-user@jugueria.pe", "hash", Role.CASHIER, NOW));
		var firstFamily = tokens.save(
				new RefreshToken(UUID.randomUUID(), user.getId(), "family-1", NOW, NOW.plus(Duration.ofDays(7)), NOW));
		var secondFamily = tokens.save(
				new RefreshToken(UUID.randomUUID(), user.getId(), "family-2", NOW, NOW.plus(Duration.ofDays(7)), NOW));
		var untouched = tokens.save(new RefreshToken(UUID.randomUUID(), otherUser.getId(), "family-3", NOW,
				NOW.plus(Duration.ofDays(7)), NOW));

		revokeAllForUser(user.getId(), NOW);

		assertThat(tokens.findById(firstFamily.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(tokens.findById(secondFamily.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(tokens.findById(untouched.getId()).orElseThrow().getRevokedAt()).isNull();
	}

	@Test
	void onlyOneOfTwoConcurrentMarkUsedCallsOnTheSameTokenSucceeds() throws InterruptedException {
		var user = accounts.save(new UserAccount("concurrent@jugueria.pe", "hash", Role.CASHIER, NOW));
		var token = tokens.save(new RefreshToken(UUID.randomUUID(), user.getId(), "concurrent", NOW,
				NOW.plus(Duration.ofDays(7)), NOW));
		var attempts = 8;
		var successes = new AtomicInteger();
		var ready = new CountDownLatch(attempts);
		var go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		try {
			for (int i = 0; i < attempts; i++) {
				pool.submit(() -> {
					ready.countDown();
					await(go);
					if (markUsed(token.getId(), NOW) == 1) {
						successes.incrementAndGet();
					}
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

		assertThat(successes.get()).isEqualTo(1);
	}

	private int markUsed(UUID id, Instant now) {
		return transactionTemplate.execute(status -> tokens.markUsed(id, now, NO_ABSOLUTE_FLOOR));
	}

	private int revokeFamily(UUID familyId, Instant now) {
		return transactionTemplate.execute(status -> tokens.revokeFamily(familyId, now));
	}

	private void revokeAllForUser(UUID userId, Instant now) {
		transactionTemplate.executeWithoutResult(status -> tokens.revokeAllForUser(userId, now));
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
