package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// Rows are inserted by hand and never resubmitted: this proves the claim, the lease, the parking and the lock skipping.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"jugueria.shared.event-resubmission.max-attempts=3" })
@Import(TestcontainersConfiguration.class)
class EventPublicationClaimsIT {

	@Autowired
	EventPublicationClaims claims;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	Clock clock;

	@AfterEach
	void clean() {
		jdbc.sql("delete from modulith.event_publication").update();
		jdbc.sql("delete from modulith.event_publication_dead_letter").update();
	}

	private UUID insert(String status, int attempts, Duration age) {
		var id = UUID.randomUUID();
		jdbc.sql("""
				insert into modulith.event_publication
				  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts)
				values (:id, 'Listener.on', 'Unknown', '{}', :published, :status, :attempts)
				""")
			.param("id", id)
			.param("published", OffsetDateTime.ofInstant(Instant.now(clock).minus(age), ZoneOffset.UTC))
			.param("status", status)
			.param("attempts", attempts)
			.update();
		return id;
	}

	private static java.util.List<UUID> ids(java.util.List<EventPublicationClaims.Publication> publications) {
		return publications.stream().map(EventPublicationClaims.Publication::id).toList();
	}

	@Test
	void claimsAnIncompletePublicationOnceUntilItsLeaseRunsOut() {
		var id = insert("FAILED", 0, Duration.ofMinutes(10));

		assertThat(ids(claims.claimBatch().toResubmit())).containsExactly(id);
		assertThat(claims.claimBatch().toResubmit()).isEmpty();
	}

	@Test
	void leavesAPublicationThatIsStillYoungToItsFirstDelivery() {
		insert("PUBLISHED", 0, Duration.ofSeconds(5));

		assertThat(claims.claimBatch().toResubmit()).isEmpty();
	}

	@Test
	void parksAPublicationThatUsedUpItsAttemptsAndKeepsItOutOfEveryLaterClaim() {
		var id = insert("FAILED", 3, Duration.ofMinutes(10));

		var claim = claims.claimBatch();

		assertThat(claim.toResubmit()).isEmpty();
		assertThat(ids(claim.deadLettered())).containsExactly(id);
		assertThat(jdbc.sql("select count(*) from modulith.event_publication where id = :id")
			.param("id", id)
			.query(Long.class)
			.single()).isZero();
		assertThat(jdbc.sql("select completion_attempts from modulith.event_publication_dead_letter where id = :id")
			.param("id", id)
			.query(Integer.class)
			.single()).isEqualTo(3);
		assertThat(claims.claimBatch().deadLettered()).isEmpty();
	}

	@Test
	void takesBackADeliveryThatWasHandedOverButNeverAnswered() {
		var id = insert("RESUBMITTED", 1, Duration.ofMinutes(10));

		assertThat(ids(claims.claimBatch().toResubmit())).containsExactly(id);
		assertThat(jdbc.sql("select status from modulith.event_publication where id = :id")
			.param("id", id)
			.query(String.class)
			.single()).isEqualTo("FAILED");
	}

	@Test
	void skipsARowAnotherTaskHasLockedInsteadOfWaitingForIt() throws Exception {
		var locked = insert("FAILED", 0, Duration.ofMinutes(10));
		var free = insert("FAILED", 0, Duration.ofMinutes(9));
		var rowLocked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
			jdbc.sql("select id from modulith.event_publication where id = :id for update").param("id", locked).query().listOfRows();
			rowLocked.countDown();
			await(release);
		}));
		assertThat(rowLocked.await(10, TimeUnit.SECONDS)).isTrue();
		try {
			assertThat(ids(claims.claimBatch().toResubmit())).containsExactly(free);
		}
		finally {
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
