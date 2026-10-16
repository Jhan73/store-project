package com.jhanantezana.jugueria.shared.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Jobs;

// Runs on every task; the SKIP LOCKED claim in IdempotencyCleanup keeps two tasks off the same rows.
@Component
class IdempotencyCleanupJob implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

	private final IdempotencyCleanup cleanup;

	private final IdempotencyProperties properties;

	IdempotencyCleanupJob(IdempotencyCleanup cleanup, IdempotencyProperties properties) {
		this.cleanup = cleanup;
		this.properties = properties;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		// A short first delay: test and prod are often stopped, so a long one could mean the sweep never runs.
		registrar.addFixedDelayTask(new IntervalTask(this::run, properties.cleanupInterval(), properties.initialDelay()));
	}

	void run() {
		var deleted = Jobs.drain(cleanup::deleteExpiredBatch);
		if (deleted > 0) {
			log.info("Deleted {} expired idempotency keys", deleted);
		}
	}

}
