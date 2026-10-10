package com.jhanantezana.jugueria.inventory.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.inventory.ReservationExpired;

@Component
class ReservationExpiry {

	private final StockReservations reservations;

	private final StockChanges changes;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ReservationExpiry(StockReservations reservations, StockChanges changes, ApplicationEventPublisher events,
			Clock clock) {
		this.reservations = reservations;
		this.changes = changes;
		this.events = events;
		this.clock = clock;
	}

	/** Releases up to one batch of lapsed reservations, products in ascending order like every other stock change. */
	@Transactional
	int expireBatch() {
		var now = Instant.now(clock);
		var lapsed = reservations.claimLapsed(now)
			.stream()
			.sorted(Comparator.comparing((StockReservations.Held held) -> held.productId().toString())
				.thenComparing(StockReservations.Held::referenceId))
			.toList();
		var references = new LinkedHashSet<UUID>();
		for (var held : lapsed) {
			changes.apply(held.productId(), StockMovementType.EXPIRE, 0, -held.quantity(), held.referenceId(), null,
					null);
			references.add(held.referenceId());
		}
		// A reference whose lines straddle two batches is announced twice; consumers must be idempotent anyway.
		references.forEach(reference -> events.publishEvent(new ReservationExpired(reference, now)));
		return lapsed.size();
	}

}
