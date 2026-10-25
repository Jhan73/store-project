package com.jhanantezana.jugueria.shared.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Jobs;

// Nothing resubmits failed listener deliveries by itself. Every task runs this, and EventPublicationClaims hands each
// publication to one of them; listeners are idempotent, so a rare double delivery is harmless.
@Component
class IncompleteEventPublicationsJob implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(IncompleteEventPublicationsJob.class);

	private final EventPublicationClaims claims;

	private final IncompleteEventPublications publications;

	private final EventResubmissionProperties properties;

	private final Clock clock;

	IncompleteEventPublicationsJob(EventPublicationClaims claims, IncompleteEventPublications publications,
			EventResubmissionProperties properties, Clock clock) {
		this.claims = claims;
		this.publications = publications;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addFixedDelayTask(new IntervalTask(this::run, properties.interval(), properties.initialDelay()));
	}

	void run() {
		var claimed = new ArrayList<EventPublicationClaims.Publication>();
		Jobs.drain(() -> {
			var claim = claims.claimBatch();
			claim.deadLettered().forEach(this::reportParked);
			claimed.addAll(claim.toResubmit());
			return claim.toResubmit().size();
		});
		if (claimed.isEmpty()) {
			return;
		}
		claimed.forEach(this::reportIfOverdue);
		var ids = new HashSet<UUID>();
		claimed.forEach(publication -> ids.add(publication.id()));
		publications.resubmitIncompletePublications(
				ResubmissionOptions.defaults().withFilter(publication -> ids.contains(publication.getIdentifier())));
	}

	private void reportParked(EventPublicationClaims.Publication publication) {
		log.error("Event publication {} for listener {} failed {} resubmissions and was parked; "
				+ "see modulith.event_publication_dead_letter", publication.id(),
				publication.listenerId(), publication.attempts());
	}

	// One line each time a publication's age crosses a multiple of max-age between two claims, not one per claim.
	private void reportIfOverdue(EventPublicationClaims.Publication publication) {
		var now = Instant.now(clock);
		var age = Duration.between(publication.publishedAt(), now);
		var previousAge = publication.previousClaim() == null ? Duration.ZERO
				: Duration.between(publication.publishedAt(), publication.previousClaim());
		var crossings = age.dividedBy(properties.maxAge());
		if (crossings > 0 && crossings > previousAge.dividedBy(properties.maxAge())) {
			log.error("Event publication {} for listener {} is still not delivered after {} and {} attempts",
					publication.id(), publication.listenerId(), age, publication.attempts());
		}
	}

}
