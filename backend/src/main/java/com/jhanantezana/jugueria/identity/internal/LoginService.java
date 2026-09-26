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

	// Unknown emails pay the same hashing cost as known ones, so timing does not reveal them.
	private final String dummyPasswordHash;

	LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AccessTokenIssuer tokenIssuer,
			IdentityProperties properties, Clock clock) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.lockout = properties.lockout();
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("no-such-account-password");
	}

	// The rejection must not roll back the failed-attempt update.
	@Transactional(noRollbackFor = BusinessException.class)
	public LoginResult login(String email, String rawPassword) {
		var now = Instant.now(clock);
		var account = accounts.findByEmailIgnoreCase(email).orElse(null);
		if (account != null && account.isLocked(now)) {
			throw locked(account.getLockedUntil());
		}
		var hashToCheck = account != null ? account.getPasswordHash() : dummyPasswordHash;
		var passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);
		if (account == null || !account.isActive() || !passwordMatches) {
			if (account != null) {
				// The row count is irrelevant: the login is rejected either way.
				accounts.registerFailedAttempt(account.getId(), now, lockout.maxFailedAttempts(),
						now.plus(lockout.lockoutDuration()));
			}
			throw invalidCredentials();
		}
		accounts.resetFailedAttempts(account.getId(), now);
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
