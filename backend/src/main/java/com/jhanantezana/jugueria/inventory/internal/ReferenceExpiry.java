package com.jhanantezana.jugueria.inventory.internal;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.inventory.ReservationExpired;

@Component
class ReferenceExpiry {

	private final StockReservations reservations;

	private final StockChanges changes;

	private final ApplicationEventPublisher events;

	ReferenceExpiry(StockReservations reservations, StockChanges changes, ApplicationEventPublisher events) {
		this.reservations = reservations;
		this.changes = changes;
		this.events = events;
	}

	/** Releases the reference's lapsed reservations, products in ascending order like every other stock change. */
	@Transactional
	int expire(UUID referenceId, Instant now) {
		var lapsed = reservations.claimLapsed(referenceId, now)
			.stream()
			.sorted(Comparator.comparing((StockReservations.Held held) -> held.productId().toString()))
			.toList();
		for (var held : lapsed) {
			changes.apply(held.productId(), StockMovementType.EXPIRE, 0, -held.quantity(), referenceId, null, null);
		}
		if (!lapsed.isEmpty()) {
			events.publishEvent(new ReservationExpired(referenceId, now));
		}
		return lapsed.size();
	}

}
