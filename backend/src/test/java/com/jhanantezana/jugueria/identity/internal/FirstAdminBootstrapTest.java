package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;

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
	UserAccountRepository accounts;

	@Mock
	StaffProvisioningService provisioning;

	FirstAdminBootstrap bootstrap;

	@BeforeEach
	void setUp() {
		bootstrap = new FirstAdminBootstrap(accounts, provisioning);
	}

	@Test
	void doesNothingWhenAnActiveAdminAlreadyExists() {
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

		var exitCode = bootstrap.run("owner@jugueria.pe");

		assertThat(exitCode).isZero();
		verify(provisioning, never()).createStaff(any(), any());
	}

	@Test
	void failsWithoutAnAdminEmailArgument() {
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(0L);

		var exitCode = bootstrap.run(null);

		assertThat(exitCode).isEqualTo(1);
		verify(provisioning, never()).createStaff(any(), any());
	}

	@Test
	void createsTheFirstAdminWhenNoneExists() {
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(0L);
		var account = new UserAccount("owner@jugueria.pe", "hash", Role.ADMIN, NOW);
		when(provisioning.createStaff("owner@jugueria.pe", Role.ADMIN))
			.thenReturn(new ProvisionedStaff(account, URI.create("https://jugueria.jhanantezana.com/set-password?token=x"),
					NOW.plusSeconds(1)));

		var exitCode = bootstrap.run("owner@jugueria.pe");

		assertThat(exitCode).isZero();
		verify(provisioning).createStaff("owner@jugueria.pe", Role.ADMIN);
	}

}
