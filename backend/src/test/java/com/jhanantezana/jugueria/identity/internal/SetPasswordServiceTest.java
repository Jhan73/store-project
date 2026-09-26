package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;

@ExtendWith(MockitoExtension.class)
class SetPasswordServiceTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	static final UUID USER_ID = UUID.randomUUID();

	@Mock
	SetPasswordTokenRepository tokens;

	@Mock
	UserAccountRepository accounts;

	@Mock
	PasswordEncoder passwordEncoder;

	SetPasswordService service;

	@BeforeEach
	void setUp() {
		service = new SetPasswordService(tokens, accounts, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void rejectsAMissingToken() {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.setPassword(null, "a-long-password"));

		verify(tokens, never()).findByTokenHash(any());
	}

	@Test
	void rejectsAnUnknownToken() {
		when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.setPassword("unknown", "a-long-password"));

		verify(tokens, never()).markUsed(any(), any());
	}

	@Test
	void rejectsATokenThatFailsTheAtomicConsumption() {
		var token = new SetPasswordToken(USER_ID, RefreshTokens.hash("raced"), NOW, NOW.plus(Duration.ofHours(48)));
		when(tokens.findByTokenHash(RefreshTokens.hash("raced"))).thenReturn(Optional.of(token));
		when(tokens.markUsed(token.getId(), NOW)).thenReturn(0);

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.setPassword("raced", "a-long-password"));

		verify(accounts, never()).findById(any());
	}

	@Test
	void setsThePasswordAndResetsLockoutOnceTheTokenIsConsumed() {
		var raw = "valid-token";
		var token = new SetPasswordToken(USER_ID, RefreshTokens.hash(raw), NOW, NOW.plus(Duration.ofHours(48)));
		var account = new UserAccount("new-staff@jugueria.pe", "unusable-hash", Role.CASHIER, NOW, 5,
				NOW.plus(Duration.ofMinutes(15)));
		when(tokens.findByTokenHash(RefreshTokens.hash(raw))).thenReturn(Optional.of(token));
		when(tokens.markUsed(token.getId(), NOW)).thenReturn(1);
		when(accounts.findById(USER_ID)).thenReturn(Optional.of(account));
		when(passwordEncoder.encode("a-long-password")).thenReturn("encoded-hash");

		service.setPassword(raw, "a-long-password");

		assertThat(account.getPasswordHash()).isEqualTo("encoded-hash");
		assertThat(account.getFailedAttempts()).isZero();
		assertThat(account.getLockedUntil()).isNull();
	}

	@Test
	void rejectsWhenTheTokensAccountNoLongerExists() {
		var raw = "orphan-token";
		var token = new SetPasswordToken(USER_ID, RefreshTokens.hash(raw), NOW, NOW.plus(Duration.ofHours(48)));
		when(tokens.findByTokenHash(RefreshTokens.hash(raw))).thenReturn(Optional.of(token));
		when(tokens.markUsed(token.getId(), NOW)).thenReturn(1);
		when(accounts.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.setPassword(raw, "a-long-password"));
	}

	@Test
	void everyRejectionUsesTheSameNonDisclosingCode() {
		when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.setPassword("unknown", "a-long-password"))
			.satisfies(ex -> org.assertj.core.api.Assertions.assertThat(ex.errorCode().code())
				.isEqualTo("auth.invalid-set-password-token"));
	}

}
