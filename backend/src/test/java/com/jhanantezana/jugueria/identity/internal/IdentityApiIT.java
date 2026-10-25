package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class IdentityApiIT {

	static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

	@Autowired
	IdentityApi identity;

	@Autowired
	UserAccountRepository accounts;

	@AfterEach
	void cleanUp() {
		accounts.deleteAll();
	}

	@Test
	void listsOnlyTheActiveAdministratorEmails() {
		accounts.save(new UserAccount("admin@jugueria.pe", "hash", Role.ADMIN, NOW));
		accounts.save(new UserAccount("former-admin@jugueria.pe", "hash", Role.ADMIN, NOW, false));
		accounts.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW));

		assertThat(identity.activeAdminEmails()).containsExactly("admin@jugueria.pe");
	}

	@Test
	void findsTheEmailOfAUserById() {
		var cashier = accounts.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW));

		assertThat(identity.emailOf(cashier.getId())).contains("cashier@jugueria.pe");
		assertThat(identity.emailOf(UUID.randomUUID())).isEmpty();
	}

	@Test
	void findsTheUsernameOfAUserByIdAndNothingWhenThereIsNone() {
		var named = new UserAccount("maria@jugueria.pe", "hash", Role.CASHIER, NOW);
		named.rename("Maria", NOW);
		named = accounts.save(named);
		var legacy = accounts.save(new UserAccount("legacy@jugueria.pe", "hash", Role.CASHIER, NOW));

		assertThat(identity.usernameOf(named.getId())).contains("Maria");
		assertThat(identity.usernameOf(legacy.getId())).isEmpty();
		assertThat(identity.usernameOf(UUID.randomUUID())).isEmpty();
	}

}
