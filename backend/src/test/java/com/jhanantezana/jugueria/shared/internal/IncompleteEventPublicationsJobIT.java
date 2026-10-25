package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

// The schedule is inert in tests, so the job is run by hand: what is proved is that a failed delivery is picked up
// again by the job alone, with nobody republishing the event.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"jugueria.shared.event-resubmission.min-age=0s" })
@Import({ TestcontainersConfiguration.class, IncompleteEventPublicationsJobIT.FlakyListener.class })
class IncompleteEventPublicationsJobIT {

	record ProbeEvent(UUID id) {
	}

	@Component
	static class FlakyListener {

		private final AtomicInteger attempts = new AtomicInteger();

		int attempts() {
			return attempts.get();
		}

		void reset() {
			attempts.set(0);
		}

		@ApplicationModuleListener
		void on(ProbeEvent event) {
			if (attempts.incrementAndGet() == 1) {
				throw new IllegalStateException("first delivery fails");
			}
		}

	}

	@Autowired
	IncompleteEventPublicationsJob job;

	@Autowired
	FlakyListener listener;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcClient jdbc;

	@Test
	void aFailedDeliveryIsRetriedByTheJobAndOnlyUntilItSucceeds() {
		listener.reset();
		transactions.executeWithoutResult(status -> events.publishEvent(new ProbeEvent(UUID.randomUUID())));
		await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts() == 1);

		job.run();

		await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts() == 2);
		job.run();
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).until(() -> listener.attempts() == 2);
		assertThat(listener.attempts()).isEqualTo(2);
	}

	// The library picks the oldest 100 FAILED rows before any filter of ours runs, so parked rows must be out of its sight.
	@Test
	void parkedPublicationsNeverStarveALaterFailedDelivery() {
		listener.reset();
		transactions.executeWithoutResult(status -> events.publishEvent(new ProbeEvent(UUID.randomUUID())));
		await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts() == 1);
		for (int i = 0; i < 101; i++) {
			jdbc.sql("""
					insert into modulith.event_publication
					  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts)
					values (:id, 'Listener.on', 'Unknown', '{}', now() - interval '1 day' - make_interval(secs => :i),
					  'FAILED', 5)
					""").param("id", UUID.randomUUID()).param("i", i).update();
		}

		job.run();

		await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts() == 2);
		jdbc.sql("delete from modulith.event_publication where event_type = 'Unknown'").update();
	}

}
