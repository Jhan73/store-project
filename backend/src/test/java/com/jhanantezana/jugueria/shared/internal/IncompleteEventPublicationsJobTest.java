package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class IncompleteEventPublicationsJobTest {

	final IncompleteEventPublications publications = mock(IncompleteEventPublications.class);

	final EventResubmissionProperties properties = new EventResubmissionProperties(Duration.ofMinutes(2),
			Duration.ofSeconds(30), Duration.ofMinutes(3));

	final IncompleteEventPublicationsJob job = new IncompleteEventPublicationsJob(publications, properties);

	@Test
	void resubmitsOnlyPublicationsOlderThanTheMinimumAge() {
		job.run();

		verify(publications).resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(3));
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
		assertThatThrownBy(() -> new EventResubmissionProperties(Duration.ZERO, Duration.ZERO, Duration.ofMinutes(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
