package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class FirstAdminBootstrapTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	@Mock
	StaffProvisioningService provisioning;

	FirstAdminBootstrap bootstrap;

	@BeforeEach
	void setUp() {
		bootstrap = new FirstAdminBootstrap(provisioning);
	}

	@Test
	void doesNothingWhenAnActiveAdminAlreadyExists() {
		when(provisioning.createFirstAdmin("owner@jugueria.pe")).thenReturn(Optional.empty());

		var exitCode = bootstrap.run("owner@jugueria.pe");

		assertThat(exitCode).isZero();
	}

	@Test
	void failsWithoutAnAdminEmailArgument() {
		var exitCode = bootstrap.run(null);

		assertThat(exitCode).isEqualTo(1);
		verify(provisioning, never()).createFirstAdmin(any());
	}

	@Test
	void createsTheFirstAdminWhenNoneExists() {
		var account = new UserAccount("owner@jugueria.pe", "hash", Role.ADMIN, NOW);
		when(provisioning.createFirstAdmin("owner@jugueria.pe")).thenReturn(Optional.of(new ProvisionedStaff(account,
				URI.create("https://jugueria.jhanantezana.com/set-password?token=x"), NOW.plusSeconds(1))));

		var exitCode = bootstrap.run("owner@jugueria.pe");

		assertThat(exitCode).isZero();
	}

}
