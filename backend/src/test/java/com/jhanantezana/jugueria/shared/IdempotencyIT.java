package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Idempotency.Registration;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"jugueria.shared.idempotency.ttl=2h", "jugueria.shared.idempotency.lock-timeout=1s" })
@Import(TestcontainersConfiguration.class)
class IdempotencyIT {

	private static final String HASH = RequestHash.of("POST", "/api/v1/tickets", null);

	private static final String OTHER_HASH = RequestHash.of("POST", "/api/v1/tickets", "other");

	@Autowired
	Idempotency idempotency;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM shared.idempotency_key").update();
	}

	@Test
	void aNewKeyProceeds() {
		var result = tx.execute(status -> idempotency.register(UUID.randomUUID(), UUID.randomUUID(), HASH));

		assertThat(result).isEqualTo(new Registration.Fresh());
	}

	@Test
	void replaysTheStoredResponseForTheSameKeyAndRequest() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		tx.executeWithoutResult(status -> {
			idempotency.register(actor, key, HASH);
			idempotency.storeResponse(actor, key, new StoredResponse(201, "{\"id\":\"t-1\"}"));
		});

		var result = tx.execute(status -> idempotency.register(actor, key, HASH));

		assertThat(result).isEqualTo(new Registration.Replay(new StoredResponse(201, "{\"id\":\"t-1\"}")));
	}

	@Test
	void rejectsTheSameKeyWithADifferentRequest() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		tx.executeWithoutResult(status -> {
			idempotency.register(actor, key, HASH);
			idempotency.storeResponse(actor, key, new StoredResponse(201, "{}"));
		});

		assertThatThrownBy(() -> tx.executeWithoutResult(status -> idempotency.register(actor, key, OTHER_HASH)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.errorCode()).isEqualTo(CommonError.IDEMPOTENCY_KEY_REUSED);
				assertThat(e.errorCode().status().value()).isEqualTo(422);
			});
	}

	@Test
	void scopesKeysByActor() {
		var key = UUID.randomUUID();
		tx.executeWithoutResult(status -> {
			idempotency.register(UUID.randomUUID(), key, HASH);
		});

		var result = tx.execute(status -> idempotency.register(UUID.randomUUID(), key, OTHER_HASH));

		assertThat(result).isEqualTo(new Registration.Fresh());
	}

	@Test
	void aRejectedCommandReleasesItsKeyForARetry() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
			idempotency.register(actor, key, HASH);
			throw new BusinessException(CommonError.CONCURRENT_MODIFICATION, "rejected");
		})).isInstanceOf(BusinessException.class);

		var retry = tx.execute(status -> idempotency.register(actor, key, OTHER_HASH));

		assertThat(retry).isEqualTo(new Registration.Fresh());
	}

	@Test
	void keepsTheKeyForTheConfiguredTimeToLive() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var before = Instant.now();
		tx.executeWithoutResult(status -> idempotency.register(actor, key, HASH));

		var expiresAt = jdbc.sql("SELECT expires_at FROM shared.idempotency_key WHERE actor_id = :a AND key = :k")
			.param("a", actor)
			.param("k", key)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();

		assertThat(expiresAt).isBetween(before.plus(Duration.ofHours(2)), Instant.now().plus(Duration.ofHours(2)));
	}

	@Test
	void reportsInProgressWhenAnotherTransactionKeepsTheKeyPastTheLockTimeout() throws SQLException {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		try (var holder = dataSource.getConnection()) {
			holder.setAutoCommit(false);
			try (var insert = holder.prepareStatement("""
					INSERT INTO shared.idempotency_key (actor_id, key, request_hash, created_at, expires_at)
					VALUES (?, ?, ?, now(), now() + interval '1 hour')
					""")) {
				insert.setObject(1, actor);
				insert.setObject(2, key);
				insert.setString(3, HASH);
				insert.executeUpdate();

				assertThatThrownBy(() -> tx.executeWithoutResult(status -> idempotency.register(actor, key, HASH)))
					.isInstanceOfSatisfying(BusinessException.class, e -> {
						assertThat(e.errorCode()).isEqualTo(CommonError.IDEMPOTENCY_IN_PROGRESS);
						assertThat(e.errorCode().status().value()).isEqualTo(409);
					});
			}
			finally {
				holder.rollback();
			}
		}
	}

	@Test
	void leavesTheCallersLockTimeoutAsItFoundIt() {
		var inside = tx.execute(status -> {
			var before = jdbc.sql("SELECT current_setting('lock_timeout')").query(String.class).single();
			idempotency.register(UUID.randomUUID(), UUID.randomUUID(), HASH);
			var after = jdbc.sql("SELECT current_setting('lock_timeout')").query(String.class).single();
			return before.equals(after);
		});

		assertThat(inside).isTrue();
	}

	@Test
	void mustRunInsideTheUseCaseTransaction() {
		assertThatThrownBy(() -> idempotency.register(UUID.randomUUID(), UUID.randomUUID(), HASH))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void aConcurrentDuplicateWaitsForTheFirstTransactionAndReplaysItsResponse() throws Exception {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var firstHoldsKey = new CountDownLatch(1);
		var releaseFirst = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var first = pool.submit(() -> tx.execute(status -> {
				var registration = idempotency.register(actor, key, HASH);
				firstHoldsKey.countDown();
				awaitQuietly(releaseFirst);
				idempotency.storeResponse(actor, key, new StoredResponse(201, "{\"id\":\"t-1\"}"));
				return registration;
			}));
			assertThat(firstHoldsKey.await(10, TimeUnit.SECONDS)).isTrue();
			var second = pool.submit(() -> tx.execute(status -> idempotency.register(actor, key, HASH)));
			awaitABackendWaitingOnTheKey();
			releaseFirst.countDown();

			assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(new Registration.Fresh());
			assertThat(second.get(10, TimeUnit.SECONDS))
				.isEqualTo(new Registration.Replay(new StoredResponse(201, "{\"id\":\"t-1\"}")));
		}
	}

	// Polls PostgreSQL itself until the second insert is blocked on the first transaction's row.
	private void awaitABackendWaitingOnTheKey() throws InterruptedException {
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			var waiting = jdbc.sql("""
					SELECT count(*) FROM pg_stat_activity
					WHERE wait_event_type = 'Lock' AND query LIKE 'INSERT INTO shared.idempotency_key%'
					""").query(Long.class).single();
			if (waiting > 0) {
				return;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("The duplicate request never blocked on the first transaction's key");
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
