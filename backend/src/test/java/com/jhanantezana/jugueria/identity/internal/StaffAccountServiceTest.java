package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jhanantezana.jugueria.identity.IdentityError;
import com.jhanantezana.jugueria.identity.UserCreated;
import com.jhanantezana.jugueria.identity.UserDeactivated;
import com.jhanantezana.jugueria.identity.UserRoleChanged;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class StaffAccountServiceTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	static final UUID ADMIN_ID = UUID.randomUUID();

	@Mock
	UserAccountRepository accounts;

	@Mock
	SetPasswordTokenService setPasswordTokens;

	@Mock
	RefreshTokenService refreshTokens;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	ApplicationEventPublisher events;

	@Mock
	CurrentActor currentActor;

	@Mock
	ActiveAdminLock activeAdminLock;

	StaffAccountService service;

	@BeforeEach
	void setUp() {
		service = new StaffAccountService(accounts, setPasswordTokens, refreshTokens, passwordEncoder, events,
				currentActor, activeAdminLock, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void rejectsCreatingACustomerAsStaff() {
		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.createAccountAndToken("customer@jugueria.pe", Role.CUSTOMER))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.INVALID_STAFF_ROLE));

		verify(accounts, never()).saveAndFlush(any());
	}

	@Test
	void normalizesTheEmailAndPublishesUserCreated() {
		when(passwordEncoder.encode(any())).thenReturn("unusable-hash");
		when(accounts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(setPasswordTokens.issue(any())).thenReturn(new IssuedSetPasswordToken("raw", NOW.plusSeconds(1)));
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(currentActor.role()).thenReturn(Role.ADMIN);

		var provisioned = service.createAccountAndToken("  Cashier@Jugueria.PE ", Role.CASHIER);

		assertThat(provisioned.account().getEmail()).isEqualTo("cashier@jugueria.pe");
		var captor = ArgumentCaptor.forClass(UserCreated.class);
		verify(events).publishEvent(captor.capture());
		assertThat(captor.getValue().email()).isEqualTo("cashier@jugueria.pe");
		assertThat(captor.getValue().actorId()).isEqualTo(ADMIN_ID);
	}

	@Test
	void translatesADuplicateEmailIntoABusinessException() {
		when(passwordEncoder.encode(any())).thenReturn("unusable-hash");
		when(accounts.saveAndFlush(any())).thenThrow(mock(DataIntegrityViolationException.class));

		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.createAccountAndToken("dup@jugueria.pe", Role.CASHIER))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.EMAIL_ALREADY_REGISTERED));
	}

	@Test
	void createFirstAdminAcquiresTheLockBeforeCounting() {
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(0L);
		when(passwordEncoder.encode(any())).thenReturn("unusable-hash");
		when(accounts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(setPasswordTokens.issue(any())).thenReturn(new IssuedSetPasswordToken("raw", NOW.plusSeconds(1)));

		var provisioned = service.createFirstAdminAccountAndToken("owner@jugueria.pe");

		assertThat(provisioned).isPresent();
		assertThat(provisioned.orElseThrow().account().getRole()).isEqualTo(Role.ADMIN);
		verify(activeAdminLock).acquire();
	}

	@Test
	void createFirstAdminIsANoOpWhenAnActiveAdminAlreadyExists() {
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

		var provisioned = service.createFirstAdminAccountAndToken("owner@jugueria.pe");

		assertThat(provisioned).isEmpty();
		verify(accounts, never()).saveAndFlush(any());
	}

	@Test
	void reissueSetPasswordTokenRejectsActingOnOnesOwnAccount() {
		when(currentActor.id()).thenReturn(ADMIN_ID);

		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.reissueSetPasswordToken(ADMIN_ID))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.CANNOT_MODIFY_OWN_ACCOUNT));

		verify(setPasswordTokens, never()).issue(any());
	}

	@Test
	void reissueSetPasswordTokenHidesACustomerAccountAsNotFound() {
		var id = UUID.randomUUID();
		when(accounts.findById(id))
			.thenReturn(Optional.of(new UserAccount("customer@jugueria.pe", "hash", Role.CUSTOMER, NOW)));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.reissueSetPasswordToken(id))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(CommonError.NOT_FOUND));
	}

	@Test
	void reissueSetPasswordTokenIssuesANewToken() {
		var account = new UserAccount("cashier@jugueria.pe", "unusable-hash", Role.CASHIER, NOW);
		when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
		when(setPasswordTokens.issue(account.getId())).thenReturn(new IssuedSetPasswordToken("raw", NOW.plusSeconds(1)));

		var provisioned = service.reissueSetPasswordToken(account.getId());

		assertThat(provisioned.rawSetPasswordToken()).isEqualTo("raw");
		verify(setPasswordTokens).issue(account.getId());
	}

	@Test
	void listExcludesCustomers() {
		service.list(PageRequest.of(0, 20));

		verify(accounts).findByRoleNot(eq(Role.CUSTOMER), any());
	}

	@Test
	void getHidesACustomerAccountAsNotFound() {
		var id = UUID.randomUUID();
		when(accounts.findById(id))
			.thenReturn(Optional.of(new UserAccount("customer@jugueria.pe", "hash", Role.CUSTOMER, NOW)));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.get(id))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(CommonError.NOT_FOUND));
	}

	@Test
	void changeRoleRejectsActingOnOnesOwnAccount() {
		when(currentActor.id()).thenReturn(ADMIN_ID);

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.changeRole(ADMIN_ID, Role.CASHIER))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.CANNOT_MODIFY_OWN_ACCOUNT));

		verify(accounts, never()).findById(any());
	}

	@Test
	void changeRoleRejectsDemotingTheLastActiveAdmin() {
		var targetId = UUID.randomUUID();
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("only-admin@jugueria.pe", "hash", Role.ADMIN, NOW)));
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.changeRole(targetId, Role.CASHIER))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.LAST_ACTIVE_ADMIN_REQUIRED));

		verify(refreshTokens, never()).revokeAllForUser(any());
	}

	@Test
	void changeRoleRevokesSessionsAndPublishesUserRoleChanged() {
		var targetId = UUID.randomUUID();
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(currentActor.role()).thenReturn(Role.ADMIN);
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW)));

		var updated = service.changeRole(targetId, Role.SERVER);

		assertThat(updated.getRole()).isEqualTo(Role.SERVER);
		verify(activeAdminLock).acquire();
		verify(refreshTokens).revokeAllForUser(targetId);
		var captor = ArgumentCaptor.forClass(UserRoleChanged.class);
		verify(events).publishEvent(captor.capture());
		assertThat(captor.getValue().oldRole()).isEqualTo(Role.CASHIER);
		assertThat(captor.getValue().newRole()).isEqualTo(Role.SERVER);
	}

	@Test
	void changeRoleIsANoOpWhenTheRoleIsUnchanged() {
		var targetId = UUID.randomUUID();
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW)));

		service.changeRole(targetId, Role.CASHIER);

		verify(refreshTokens, never()).revokeAllForUser(any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void deactivateRejectsActingOnOnesOwnAccount() {
		when(currentActor.id()).thenReturn(ADMIN_ID);

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.deactivate(ADMIN_ID))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.CANNOT_MODIFY_OWN_ACCOUNT));
	}

	@Test
	void deactivateRejectsTheLastActiveAdmin() {
		var targetId = UUID.randomUUID();
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("only-admin@jugueria.pe", "hash", Role.ADMIN, NOW)));
		when(accounts.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.deactivate(targetId))
			.satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(IdentityError.LAST_ACTIVE_ADMIN_REQUIRED));
	}

	@Test
	void deactivateRevokesSessionsAndUnusedTokensAndPublishesUserDeactivated() {
		var targetId = UUID.randomUUID();
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(currentActor.role()).thenReturn(Role.ADMIN);
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW)));

		var updated = service.deactivate(targetId);

		assertThat(updated.isActive()).isFalse();
		verify(activeAdminLock).acquire();
		verify(refreshTokens).revokeAllForUser(targetId);
		verify(setPasswordTokens).revokeAllUnused(targetId);
		var captor = ArgumentCaptor.forClass(UserDeactivated.class);
		verify(events).publishEvent(captor.capture());
		assertThat(captor.getValue().userId()).isEqualTo(targetId);
	}

	@Test
	void deactivateIsIdempotent() {
		var targetId = UUID.randomUUID();
		when(currentActor.id()).thenReturn(ADMIN_ID);
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW, false)));

		service.deactivate(targetId);

		verify(refreshTokens, never()).revokeAllForUser(any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void reactivateSetsActiveWithoutPublishingAnEvent() {
		var targetId = UUID.randomUUID();
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW, false)));

		var updated = service.reactivate(targetId);

		assertThat(updated.isActive()).isTrue();
		verify(events, never()).publishEvent(any());
	}

	@Test
	void reactivateIsIdempotent() {
		var targetId = UUID.randomUUID();
		when(accounts.findById(targetId))
			.thenReturn(Optional.of(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW)));

		service.reactivate(targetId);

		verify(events, never()).publishEvent(any());
	}

}
