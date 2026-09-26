package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.AuthError;
import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.shared.BusinessException;

@Service
public class LoginService {

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenIssuer tokenIssuer;

	private final IdentityProperties.Lockout lockout;

	private final Clock clock;

	LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AccessTokenIssuer tokenIssuer,
			IdentityProperties properties, Clock clock) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.lockout = properties.lockout();
		this.clock = clock;
	}

	// The failed-attempt counter must survive even though this method then throws: BusinessException
	// would otherwise roll back the very update that records the failure.
	@Transactional(noRollbackFor = BusinessException.class)
	public LoginResult login(String email, String rawPassword) {
		var now = Instant.now(clock);
		var account = accounts.findByEmailIgnoreCase(email).orElse(null);
		if (account == null) {
			throw invalidCredentials();
		}
		if (account.isLocked(now)) {
			throw locked(account.getLockedUntil());
		}
		if (!account.isActive() || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
			accounts.registerFailedAttempt(account.getId(), lockout.maxFailedAttempts(),
					now.plus(lockout.lockoutDuration()));
			throw invalidCredentials();
		}
		accounts.resetFailedAttempts(account.getId());
		return new LoginResult(tokenIssuer.issue(account.getId(), account.getRole()), account.getId(),
				account.getRole());
	}

	private static BusinessException invalidCredentials() {
		return new BusinessException(AuthError.INVALID_CREDENTIALS, "Invalid email or password");
	}

	private static BusinessException locked(Instant lockedUntil) {
		return new BusinessException(AuthError.ACCOUNT_LOCKED, "Account is locked", Map.of("lockedUntil", lockedUntil));
	}

}
