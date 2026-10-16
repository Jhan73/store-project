package com.jhanantezana.jugueria.inventory.internal;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Releases lapsed reservations one reference per transaction, so a reference that cannot be released is logged and
 * retried on the next run instead of rolling back, and repeating forever, everything claimed with it.
 */
@Component
class ReservationExpiry {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpiry.class);

	private final StockReservations reservations;

	private final ReferenceExpiry referenceExpiry;

	private final Clock clock;

	ReservationExpiry(StockReservations reservations, ReferenceExpiry referenceExpiry, Clock clock) {
		this.reservations = reservations;
		this.referenceExpiry = referenceExpiry;
		this.clock = clock;
	}

	/** @return the number of reservations released, for at most one batch of lapsed ones */
	int expireBatch() {
		var now = Instant.now(clock);
		var expired = 0;
		for (var referenceId : reservations.lapsedReferences(now)) {
			try {
				expired += referenceExpiry.expire(referenceId, now);
			}
			catch (RuntimeException failure) {
				log.error("Could not expire the stock reservations of {}", referenceId, failure);
			}
		}
		return expired;
	}

}
