package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.Role;

class UserAccountTest {

	static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

	@Test
	void isNotLockedWithoutALockedUntil() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER);

		assertThat(account.isLocked(NOW)).isFalse();
	}

	@Test
	void isLockedWhileLockedUntilIsInTheFuture() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW.plus(Duration.ofMinutes(15)));

		assertThat(account.isLocked(NOW)).isTrue();
	}

	@Test
	void isNoLongerLockedOnceLockedUntilHasPassed() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW.minus(Duration.ofSeconds(1)));

		assertThat(account.isLocked(NOW)).isFalse();
	}

}
