package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Jobs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class IdempotencyCleanupIT {

	@Autowired
	IdempotencyCleanup cleanup;

	@Autowired
	IdempotencyCleanupJob job;

	@Autowired
	ScheduledTaskHolder scheduledTasks;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM shared.idempotency_key").update();
	}

	@Test
	void deletesExpiredKeysAndKeepsLiveOnes() {
		insert(Instant.now().minus(Duration.ofMinutes(1)));
		var live = insert(Instant.now().plus(Duration.ofHours(1)));

		var deleted = Jobs.drain(cleanup::deleteExpiredBatch);

		assertThat(deleted).isEqualTo(1);
		assertThat(remainingKeys()).containsExactly(live);
	}

	@Test
	void drainsMoreExpiredKeysThanOneBatchHolds() {
		for (var i = 0; i < Jobs.BATCH_SIZE * 2 + 30; i++) {
			insert(Instant.now().minus(Duration.ofMinutes(1)));
		}

		assertThat(cleanup.deleteExpiredBatch()).isEqualTo(Jobs.BATCH_SIZE);
		assertThat(Jobs.drain(cleanup::deleteExpiredBatch)).isEqualTo(Jobs.BATCH_SIZE + 30);
		assertThat(remainingKeys()).isEmpty();
	}

	@Test
	void theScheduledJobSweepsExpiredKeys() {
		insert(Instant.now().minus(Duration.ofMinutes(1)));

		job.run();

		assertThat(remainingKeys()).isEmpty();
	}

	@Test
	void theJobIsScheduledAtTheConfiguredInterval() {
		assertThat(scheduledTasks.getScheduledTasks()).anyMatch(
				task -> task.getTask() instanceof FixedDelayTask delay && delay.getIntervalDuration().equals(Duration.ofHours(1)));
	}

	// A second task running the same job must skip rows another task has claimed, never wait on them.
	@Test
	void skipsRowsAnotherTaskHasAlreadyClaimed() throws SQLException {
		for (var i = 0; i < Jobs.BATCH_SIZE + 50; i++) {
			insert(Instant.now().minus(Duration.ofMinutes(1)));
		}
		try (var otherTask = dataSource.getConnection()) {
			otherTask.setAutoCommit(false);
			try (var claim = otherTask.createStatement()) {
				claim.execute("SELECT 1 FROM shared.idempotency_key WHERE expires_at <= now() ORDER BY expires_at "
						+ "LIMIT " + Jobs.BATCH_SIZE + " FOR UPDATE SKIP LOCKED");

				var deleted = Jobs.drain(cleanup::deleteExpiredBatch);

				assertThat(deleted).isEqualTo(50);
				assertThat(remainingKeys()).hasSize(Jobs.BATCH_SIZE);
			}
			finally {
				otherTask.rollback();
			}
		}
	}

	private UUID insert(Instant expiresAt) {
		var key = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO shared.idempotency_key (actor_id, key, request_hash, created_at, expires_at)
				VALUES (:actor, :key, :hash, :createdAt, :expiresAt)
				""")
			.param("actor", UUID.randomUUID())
			.param("key", key)
			.param("hash", "0".repeat(64))
			.param("createdAt", OffsetDateTime.ofInstant(expiresAt.minus(Duration.ofHours(24)), ZoneOffset.UTC))
			.param("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
			.update();
		return key;
	}

	private List<UUID> remainingKeys() {
		return jdbc.sql("SELECT key FROM shared.idempotency_key ORDER BY key").query(UUID.class).list();
	}

}
