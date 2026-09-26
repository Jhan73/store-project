package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class StaffProvisioningServiceTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	@Mock
	StaffAccountService staffAccounts;

	@Mock
	NotificationsApi notifications;

	StaffProvisioningService service;

	@BeforeEach
	void setUp() {
		var jwt = new IdentityProperties.Jwt("issuer", "audience", "kid", null, true, Duration.ofMinutes(15));
		var lockout = new IdentityProperties.Lockout(5, Duration.ofMinutes(15));
		var refreshToken = new IdentityProperties.RefreshToken(Duration.ofDays(7), Duration.ofDays(30));
		var setPassword = new IdentityProperties.SetPassword("https://jugueria.jhanantezana.com", "/set-password",
				Duration.ofHours(48));
		var properties = new IdentityProperties(List.of("http://localhost"), jwt, lockout, refreshToken, setPassword);
		service = new StaffProvisioningService(staffAccounts, notifications, properties);
	}

	@Test
	void buildsTheLinkAndSendsTheEmail() {
		var account = new UserAccount("new-staff@jugueria.pe", "hash", Role.CASHIER, NOW);
		when(staffAccounts.createAccountAndToken("new-staff@jugueria.pe", Role.CASHIER))
			.thenReturn(new StaffProvisioned(account, "raw-token", NOW.plus(Duration.ofHours(48))));

		var provisioned = service.createStaff("new-staff@jugueria.pe", Role.CASHIER);

		assertThat(provisioned.setPasswordLink().toString())
			.startsWith("https://jugueria.jhanantezana.com/set-password?token=");
		verify(notifications).sendStaffSetPasswordEmail(eq("new-staff@jugueria.pe"), any(), any());
	}

	@Test
	void toleratesTheEmailSenderFailingWithoutFailingTheCreation() {
		var account = new UserAccount("new-staff@jugueria.pe", "hash", Role.CASHIER, NOW);
		when(staffAccounts.createAccountAndToken(any(), any()))
			.thenReturn(new StaffProvisioned(account, "raw-token", NOW.plus(Duration.ofHours(48))));
		doThrow(new IllegalStateException("no transport")).when(notifications)
			.sendStaffSetPasswordEmail(any(), any(), any());

		assertThatCode(() -> service.createStaff("new-staff@jugueria.pe", Role.CASHIER)).doesNotThrowAnyException();
	}

	@Test
	void createFirstAdminSendsTheEmailOnlyWhenAnAccountWasCreated() {
		var account = new UserAccount("owner@jugueria.pe", "hash", Role.ADMIN, NOW);
		when(staffAccounts.createFirstAdminAccountAndToken("owner@jugueria.pe"))
			.thenReturn(Optional.of(new StaffProvisioned(account, "raw-token", NOW.plus(Duration.ofHours(48)))));

		var provisioned = service.createFirstAdmin("owner@jugueria.pe");

		assertThat(provisioned).isPresent();
		verify(notifications).sendStaffSetPasswordEmail(eq("owner@jugueria.pe"), any(), any());
	}

	@Test
	void createFirstAdminIsANoOpWithoutSendingAnyEmail() {
		when(staffAccounts.createFirstAdminAccountAndToken("owner@jugueria.pe")).thenReturn(Optional.empty());

		var provisioned = service.createFirstAdmin("owner@jugueria.pe");

		assertThat(provisioned).isEmpty();
		verify(notifications, never()).sendStaffSetPasswordEmail(any(), any(), any());
	}

	@Test
	void resendSetPasswordLinkReissuesAndSendsTheEmail() {
		var account = new UserAccount("cashier@jugueria.pe", "unusable-hash", Role.CASHIER, NOW);
		var id = account.getId();
		when(staffAccounts.reissueSetPasswordToken(id))
			.thenReturn(new StaffProvisioned(account, "new-raw-token", NOW.plus(Duration.ofHours(48))));

		service.resendSetPasswordLink(id);

		verify(notifications).sendStaffSetPasswordEmail(eq("cashier@jugueria.pe"), any(), any());
	}

}
