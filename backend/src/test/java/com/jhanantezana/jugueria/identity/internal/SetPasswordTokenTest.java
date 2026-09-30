package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SetPasswordTokenTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	@Test
	void isNotExpiredBeforeItsExpiry() {
		var token = new SetPasswordToken(UUID.randomUUID(), "hash", NOW, NOW.plus(Duration.ofHours(48)));

		assertThat(token.isExpired(NOW)).isFalse();
	}

	@Test
	void isExpiredOnceExpiresAtHasPassed() {
		var token = new SetPasswordToken(UUID.randomUUID(), "hash", NOW, NOW.minusSeconds(1));

		assertThat(token.isExpired(NOW)).isTrue();
	}

}
