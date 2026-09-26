package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;

// Timing parity is verified by interaction; a timing-based test would be flaky.
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

	static final String DUMMY_HASH = "dummy-hash-for-timing-parity";

	@Mock
	UserAccountRepository accounts;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	AccessTokenIssuer tokenIssuer;

	@Mock
	RefreshTokenService refreshTokens;

	LoginService service;

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode(any())).thenReturn(DUMMY_HASH);
		var lockout = new IdentityProperties.Lockout(5, Duration.ofMinutes(15));
		var jwt = new IdentityProperties.Jwt("issuer", "audience", "kid", null, true, Duration.ofMinutes(15));
		var refreshToken = new IdentityProperties.RefreshToken(Duration.ofDays(7), Duration.ofDays(30));
		var properties = new IdentityProperties(List.of("http://localhost"), jwt, lockout, refreshToken);
		lenient().when(refreshTokens.issueFamily(any())).thenReturn(
				new IssuedRefreshToken("raw-refresh-token", NOW.plus(Duration.ofDays(7)), Duration.ofDays(7)));
		service = new LoginService(accounts, passwordEncoder, tokenIssuer, refreshTokens, properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void checksThePasswordAgainstADummyHashForAnUnknownEmail() {
		when(accounts.findByEmailIgnoreCase("nobody@jugueria.pe")).thenReturn(Optional.empty());

		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.login("nobody@jugueria.pe", "whatever"));

		verify(passwordEncoder).matches(eq("whatever"), eq(DUMMY_HASH));
	}

	@Test
	void checksThePasswordForAnInactiveAccountInsteadOfSkippingIt() {
		var account = new UserAccount("cashier@jugueria.pe", "real-hash", Role.CASHIER, NOW, false);
		when(accounts.findByEmailIgnoreCase("cashier@jugueria.pe")).thenReturn(Optional.of(account));
		when(passwordEncoder.matches(any(), any())).thenReturn(true);

		assertThatExceptionOfType(BusinessException.class)
			.isThrownBy(() -> service.login("cashier@jugueria.pe", "whatever"));

		verify(passwordEncoder).matches(eq("whatever"), eq("real-hash"));
	}

}
