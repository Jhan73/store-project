package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class IncompleteEventPublicationsJobTest {

	final EventPublicationClaims claims = mock(EventPublicationClaims.class);

	final IncompleteEventPublications publications = mock(IncompleteEventPublications.class);

	final EventResubmissionProperties properties = new EventResubmissionProperties(Duration.ofMinutes(2),
			Duration.ofSeconds(30), Duration.ofMinutes(3), 5, Duration.ofHours(1));

	final Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);

	final IncompleteEventPublicationsJob job = new IncompleteEventPublicationsJob(claims, publications, properties,
			clock);

	private static EventPublicationClaims.Publication publication(UUID id) {
		return new EventPublicationClaims.Publication(id, "Listener.on", Instant.parse("2026-10-25T09:50:00Z"), 1);
	}

	private static EventPublication stored(UUID id) {
		var stored = mock(EventPublication.class);
		when(stored.getIdentifier()).thenReturn(id);
		return stored;
	}

	@Test
	void resubmitsOnlyThePublicationsThisTaskClaimed() {
		var mine = UUID.randomUUID();
		var someoneElses = UUID.randomUUID();
		when(claims.claimBatch()).thenReturn(new EventPublicationClaims.Claim(List.of(publication(mine)), List.of()))
			.thenReturn(new EventPublicationClaims.Claim(List.of(), List.of()));

		job.run();

		var options = ArgumentCaptor.forClass(ResubmissionOptions.class);
		verify(publications).resubmitIncompletePublications(options.capture());
		assertThat(options.getValue().getFilter().test(stored(mine))).isTrue();
		assertThat(options.getValue().getFilter().test(stored(someoneElses))).isFalse();
	}

	@Test
	void resubmitsNothingWhenNothingWasClaimed() {
		when(claims.claimBatch()).thenReturn(new EventPublicationClaims.Claim(List.of(), List.of()));

		job.run();

		verify(publications, never()).resubmitIncompletePublications(org.mockito.ArgumentMatchers.any(ResubmissionOptions.class));
	}

	@Test
	void aParkedPublicationIsNotResubmitted() {
		when(claims.claimBatch())
			.thenReturn(new EventPublicationClaims.Claim(List.of(), List.of(publication(UUID.randomUUID()))));

		job.run();

		verify(publications, never()).resubmitIncompletePublications(org.mockito.ArgumentMatchers.any(ResubmissionOptions.class));
	}

	@Test
	void registersAFixedDelayTaskWithTheConfiguredTimings() {
		var registrar = new ScheduledTaskRegistrar();

		job.configureTasks(registrar);

		assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
			assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(2));
			assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(30));
		});
	}

	@Test
	void rejectsAnIntervalThatIsNotPositive() {
		assertThatThrownBy(() -> new EventResubmissionProperties(Duration.ZERO, Duration.ZERO, Duration.ofMinutes(1), 5,
				Duration.ofHours(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAnAttemptLimitBelowOne() {
		assertThatThrownBy(() -> new EventResubmissionProperties(Duration.ofMinutes(1), Duration.ZERO,
				Duration.ofMinutes(1), 0, Duration.ofHours(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
