package com.jhanantezana.jugueria.inventory.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Jobs;

// Runs on every task; overlapping selections are harmless because claimLapsed re-checks status under SKIP LOCKED.
@Component
class ReservationExpiryJob implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

	private final ReservationExpiry expiry;

	private final InventoryProperties properties;

	ReservationExpiryJob(ReservationExpiry expiry, InventoryProperties properties) {
		this.expiry = expiry;
		this.properties = properties;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addFixedDelayTask(
				new IntervalTask(this::run, properties.reservationSweepInterval(), properties.reservationSweepInitialDelay()));
	}

	void run() {
		var expired = Jobs.drain(expiry::expireBatch);
		if (expired > 0) {
			log.info("Expired {} stock reservations", expired);
		}
	}

}
