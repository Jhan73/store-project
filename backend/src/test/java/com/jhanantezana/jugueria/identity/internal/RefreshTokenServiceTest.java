package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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

import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

	static final Duration IDLE_TTL = Duration.ofDays(7);

	static final Duration ABSOLUTE_TTL = Duration.ofDays(30);

	static final UUID USER_ID = UUID.randomUUID();

	@Mock
	RefreshTokenRepository tokens;

	@Mock
	UserAccountRepository accounts;

	@Mock
	AccessTokenIssuer accessTokens;

	RefreshTokenService service;

	@BeforeEach
	void setUp() {
		var properties = new IdentityProperties(java.util.List.of("http://localhost"),
				new IdentityProperties.Jwt("issuer", "audience", "kid", null, true, Duration.ofMinutes(15)),
				new IdentityProperties.Lockout(5, Duration.ofMinutes(15)),
				new IdentityProperties.RefreshToken(IDLE_TTL, ABSOLUTE_TTL),
				new IdentityProperties.SetPassword("http://localhost:4200", "/set-password", Duration.ofHours(48)));
		service = new RefreshTokenService(tokens, accounts, accessTokens, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void issuingAFamilySavesATokenAndReturnsItsRawValueAndExpiry() {
		var issued = service.issueFamily(USER_ID);

		assertThat(issued.rawToken()).isNotBlank();
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(IDLE_TTL));
		assertThat(issued.maxAge()).isEqualTo(IDLE_TTL);
	}

	@Test
	void issueFamilyStartsANewFamilyWithAFreshStart() {
		service.issueFamily(USER_ID);

		var captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(tokens).save(captor.capture());
		assertThat(captor.getValue().getFamilyStartedAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsAMissingToken() {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate(null));
	}

	@Test
	void rejectsAnUnknownToken() {
		when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("unknown"));

		verify(tokens, never()).revokeFamily(any(), any());
	}

	@Test
	void rejectsAnIdleExpiredTokenWithoutRevokingTheFamily() {
		var token = new RefreshToken(UUID.randomUUID(), USER_ID, "hash", NOW.minus(Duration.ofDays(8)),
				NOW.minusSeconds(1), NOW.minus(Duration.ofDays(8)));
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("expired"));

		verify(tokens, never()).revokeFamily(any(), any());
	}

	@Test
	void rejectsRotationAfterTheAbsoluteLifetimeEvenWithinTheIdleWindow() {
		var familyStartedAt = NOW.minus(ABSOLUTE_TTL).minusSeconds(1);
		var token = new RefreshToken(UUID.randomUUID(), USER_ID, "hash", NOW.minus(Duration.ofHours(1)),
				NOW.plus(Duration.ofDays(1)), familyStartedAt);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("stale-family"));

		verify(accounts, never()).findById(any());
		verify(tokens, never()).markUsed(any(), any(), any());
		verify(tokens, never()).revokeFamily(any(), any());
	}

	@Test
	void rejectsAnAlreadyRevokedTokenWithoutRevokingAgain() {
		var token = new RefreshToken(UUID.randomUUID(), USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW, null,
				NOW.minusSeconds(5));
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("revoked"));

		verify(tokens, never()).revokeFamily(any(), any());
	}

	@Test
	void revokesTheWholeFamilyWhenAnAlreadyUsedTokenIsPresentedAgain() {
		var familyId = UUID.randomUUID();
		var token = new RefreshToken(familyId, USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW,
				NOW.minusSeconds(5), null);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("reused"));

		verify(tokens).revokeFamily(eq(familyId), eq(NOW));
		verify(accounts, never()).findById(any());
	}

	@Test
	void revokesTheWholeFamilyWhenTheAtomicRotationLosesARace() {
		var familyId = UUID.randomUUID();
		var token = new RefreshToken(familyId, USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(new UserAccount("racer@jugueria.pe", "hash",
				Role.CASHIER, NOW)));
		when(tokens.markUsed(eq(token.getId()), eq(NOW), any())).thenReturn(0);

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("raced"));

		verify(tokens).revokeFamily(eq(familyId), eq(NOW));
		verify(tokens, never()).save(any());
	}

	@Test
	void rejectsAnInactiveUsersTokenWithoutRotatingIt() {
		var token = new RefreshToken(UUID.randomUUID(), USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
		when(accounts.findById(USER_ID))
			.thenReturn(Optional.of(new UserAccount("inactive@jugueria.pe", "hash", Role.CASHIER, NOW, false)));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("inactive"));

		verify(tokens, never()).markUsed(any(), any(), any());
	}

	@Test
	void rejectsALockedUsersTokenWithoutRotatingIt() {
		var token = new RefreshToken(UUID.randomUUID(), USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(
				new UserAccount("locked@jugueria.pe", "hash", Role.CASHIER, NOW, 5, NOW.plus(Duration.ofMinutes(15)))));

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.rotate("locked"));

		verify(tokens, never()).markUsed(any(), any(), any());
	}

	@Test
	void rotatesSuccessfullyAndIssuesANewAccessToken() {
		var familyId = UUID.randomUUID();
		var token = new RefreshToken(familyId, USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW);
		var account = new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, NOW);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(account));
		when(tokens.markUsed(eq(token.getId()), eq(NOW), eq(NOW.minus(ABSOLUTE_TTL)))).thenReturn(1);
		when(accessTokens.issue(account.getId(), Role.CASHIER)).thenReturn("new-access-token");

		var result = service.rotate("valid");

		assertThat(result.accessToken()).isEqualTo("new-access-token");
		assertThat(result.refreshToken()).isNotBlank();
		assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plus(IDLE_TTL));
		assertThat(result.refreshTokenMaxAge()).isEqualTo(IDLE_TTL);
		assertThat(result.userId()).isEqualTo(account.getId());
		assertThat(result.role()).isEqualTo(Role.CASHIER);
		verify(tokens).save(any(RefreshToken.class));
		verify(tokens, never()).revokeFamily(any(), any());
	}

	@Test
	void rotationCapsExpiresAtAndMaxAgeNearTheAbsoluteLimit() {
		var familyId = UUID.randomUUID();
		var familyStartedAt = NOW.minus(Duration.ofDays(25));
		var token = new RefreshToken(familyId, USER_ID, "hash", NOW.minus(Duration.ofHours(1)),
				NOW.plus(Duration.ofDays(1)), familyStartedAt);
		var account = new UserAccount("nearcap@jugueria.pe", "hash", Role.CASHIER, NOW);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(account));
		when(tokens.markUsed(eq(token.getId()), eq(NOW), eq(NOW.minus(ABSOLUTE_TTL)))).thenReturn(1);
		when(accessTokens.issue(account.getId(), Role.CASHIER)).thenReturn("new-access-token");

		var result = service.rotate("near-cap");

		var absoluteDeadline = familyStartedAt.plus(ABSOLUTE_TTL);
		assertThat(result.refreshTokenExpiresAt()).isEqualTo(absoluteDeadline);
		assertThat(result.refreshTokenMaxAge()).isEqualTo(Duration.between(NOW, absoluteDeadline));
	}

	@Test
	void logoutIsANoOpWhenNoTokenIsPresented() {
		service.logout(null);

		verify(tokens, never()).findByTokenHash(any());
	}

	@Test
	void logoutIsANoOpForAnUnknownToken() {
		when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

		service.logout("unknown");

		verify(tokens, never()).revokeFamily(any(), any());
	}

	@Test
	void logoutRevokesTheFamilyOfAKnownToken() {
		var familyId = UUID.randomUUID();
		var token = new RefreshToken(familyId, USER_ID, "hash", NOW, NOW.plus(Duration.ofDays(7)), NOW);
		when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

		service.logout("known");

		verify(tokens).revokeFamily(eq(familyId), eq(NOW));
	}

	@Test
	void revokeAllForUserDelegatesToTheRepository() {
		service.revokeAllForUser(USER_ID);

		verify(tokens).revokeAllForUser(eq(USER_ID), eq(NOW));
	}

}
