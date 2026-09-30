package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SetPasswordTokenRepositoryIT {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	@Autowired
	SetPasswordTokenRepository tokens;

	@Autowired
	UserAccountRepository accounts;

	// Neither repository has a transaction of its own; standalone calls need one.
	@Autowired
	TransactionTemplate transactionTemplate;

	@AfterEach
	void cleanUp() {
		tokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void roundTripsATokenAndFindsItByHash() {
		var user = accounts.save(new UserAccount("new-staff@jugueria.pe", "hash", Role.CASHIER, NOW));
		var saved = tokens.save(new SetPasswordToken(user.getId(), "hash-value", NOW, NOW.plus(Duration.ofHours(48))));

		var found = tokens.findByTokenHash("hash-value").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getUserId()).isEqualTo(user.getId());
		assertThat(found.getUsedAt()).isNull();
		assertThat(found.getRevokedAt()).isNull();
	}

	@Test
	void marksATokenUsedOnlyOnce() {
		var user = accounts.save(new UserAccount("once@jugueria.pe", "hash", Role.CASHIER, NOW));
		var token = tokens.save(new SetPasswordToken(user.getId(), "once", NOW, NOW.plus(Duration.ofHours(48))));

		var first = markUsed(token.getId(), NOW);
		var second = markUsed(token.getId(), NOW.plusSeconds(1));

		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
	}

	@Test
	void doesNotMarkAnExpiredTokenAsUsed() {
		var user = accounts.save(new UserAccount("expired@jugueria.pe", "hash", Role.CASHIER, NOW));
		var token = tokens.save(new SetPasswordToken(user.getId(), "expired", NOW, NOW.minusSeconds(1)));

		assertThat(markUsed(token.getId(), NOW)).isZero();
	}

	@Test
	void doesNotMarkARevokedTokenAsUsed() {
		var user = accounts.save(new UserAccount("revoked@jugueria.pe", "hash", Role.CASHIER, NOW));
		var token = tokens.save(new SetPasswordToken(user.getId(), "revoked", NOW, NOW.plus(Duration.ofHours(48))));
		revokeAllUnusedForUser(user.getId(), NOW);

		assertThat(markUsed(token.getId(), NOW)).isZero();
	}

	@Test
	void revokingLeavesAlreadyUsedTokensAlone() {
		var user = accounts.save(new UserAccount("mixed@jugueria.pe", "hash", Role.CASHIER, NOW));
		var used = tokens.save(new SetPasswordToken(user.getId(), "used", NOW, NOW.plus(Duration.ofHours(48))));
		markUsed(used.getId(), NOW);
		var unused = tokens.save(new SetPasswordToken(user.getId(), "unused", NOW, NOW.plus(Duration.ofHours(48))));

		var affected = revokeAllUnusedForUser(user.getId(), NOW.plusSeconds(1));

		assertThat(affected).isEqualTo(1);
		assertThat(tokens.findById(used.getId()).orElseThrow().getRevokedAt()).isNull();
		assertThat(tokens.findById(unused.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	void revokingAUsersTokensLeavesOtherUsersTokensAlone() {
		var user = accounts.save(new UserAccount("target@jugueria.pe", "hash", Role.CASHIER, NOW));
		var otherUser = accounts.save(new UserAccount("other@jugueria.pe", "hash", Role.CASHIER, NOW));
		var owned = tokens.save(new SetPasswordToken(user.getId(), "owned", NOW, NOW.plus(Duration.ofHours(48))));
		var untouched = tokens
			.save(new SetPasswordToken(otherUser.getId(), "untouched", NOW, NOW.plus(Duration.ofHours(48))));

		revokeAllUnusedForUser(user.getId(), NOW);

		assertThat(tokens.findById(owned.getId()).orElseThrow().getRevokedAt()).isEqualTo(NOW);
		assertThat(tokens.findById(untouched.getId()).orElseThrow().getRevokedAt()).isNull();
	}

	private int markUsed(UUID id, Instant now) {
		return transactionTemplate.execute(status -> tokens.markUsed(id, now));
	}

	private int revokeAllUnusedForUser(UUID userId, Instant now) {
		return transactionTemplate.execute(status -> tokens.revokeAllUnusedForUser(userId, now));
	}

}
