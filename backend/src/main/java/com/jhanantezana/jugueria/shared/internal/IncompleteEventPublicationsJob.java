package com.jhanantezana.jugueria.shared.internal;

import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

// Nothing resubmits failed listener deliveries by itself; listeners are idempotent, so every task may run this.
@Component
class IncompleteEventPublicationsJob implements SchedulingConfigurer {

	private final IncompleteEventPublications publications;

	private final EventResubmissionProperties properties;

	IncompleteEventPublicationsJob(IncompleteEventPublications publications, EventResubmissionProperties properties) {
		this.publications = publications;
		this.properties = properties;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addFixedDelayTask(new IntervalTask(this::run, properties.interval(), properties.initialDelay()));
	}

	void run() {
		publications.resubmitIncompletePublicationsOlderThan(properties.minAge());
	}

}
