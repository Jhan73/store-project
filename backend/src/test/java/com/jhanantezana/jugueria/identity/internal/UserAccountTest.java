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
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW);

		assertThat(account.isLocked(NOW)).isFalse();
	}

	@Test
	void isLockedWhileLockedUntilIsInTheFuture() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW, 5,
				NOW.plus(Duration.ofMinutes(15)));

		assertThat(account.isLocked(NOW)).isTrue();
	}

	@Test
	void isNoLongerLockedOnceLockedUntilHasPassed() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW, 5,
				NOW.minus(Duration.ofSeconds(1)));

		assertThat(account.isLocked(NOW)).isFalse();
	}

	@Test
	void changesRoleAndStampsUpdatedAt() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW);
		var later = NOW.plusSeconds(60);

		account.changeRole(Role.ADMIN, later);

		assertThat(account.getRole()).isEqualTo(Role.ADMIN);
		assertThat(account.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void deactivateClearsActiveAndStampsUpdatedAt() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW);
		var later = NOW.plusSeconds(60);

		account.deactivate(later);

		assertThat(account.isActive()).isFalse();
		assertThat(account.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void reactivateSetsActiveAndStampsUpdatedAt() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW, false);
		var later = NOW.plusSeconds(60);

		account.reactivate(later);

		assertThat(account.isActive()).isTrue();
		assertThat(account.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void changePasswordAlsoClearsAnyLockout() {
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW, 5,
				NOW.plus(Duration.ofMinutes(15)));
		var later = NOW.plusSeconds(60);

		account.changePassword("new-hash", later);

		assertThat(account.getPasswordHash()).isEqualTo("new-hash");
		assertThat(account.getFailedAttempts()).isZero();
		assertThat(account.getLockedUntil()).isNull();
		assertThat(account.getUpdatedAt()).isEqualTo(later);
	}

}
