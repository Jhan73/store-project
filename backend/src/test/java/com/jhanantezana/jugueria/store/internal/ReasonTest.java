package com.jhanantezana.jugueria.store.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.store.ReasonType;

class ReasonTest {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	@Test
	void createsAnActiveReason() {
		var reason = new Reason(ReasonType.VOID, "Customer changed mind", NOW);

		assertThat(reason.getType()).isEqualTo(ReasonType.VOID);
		assertThat(reason.getCode()).isEqualTo("Customer changed mind");
		assertThat(reason.isActive()).isTrue();
	}

	@Test
	void deactivateAndReactivateToggleActive() {
		var reason = new Reason(ReasonType.COMP, "Manager comp", NOW);

		reason.deactivate(NOW.plusSeconds(60));
		assertThat(reason.isActive()).isFalse();

		reason.reactivate(NOW.plusSeconds(120));
		assertThat(reason.isActive()).isTrue();
	}

}
