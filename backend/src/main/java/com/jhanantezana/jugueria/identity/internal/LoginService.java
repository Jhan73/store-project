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

	// Computed once with the real encoder so an unknown email costs the same BCrypt work as a known
	// one; a random value would do, its point is only to be a valid hash for this encoder to check.
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

	// The failed-attempt counter must survive even though this method then throws: BusinessException
	// would otherwise roll back the very update that records the failure.
	@Transactional(noRollbackFor = BusinessException.class)
	public LoginResult login(String email, String rawPassword) {
		var now = Instant.now(clock);
		var account = accounts.findByEmailIgnoreCase(email).orElse(null);
		if (account != null && account.isLocked(now)) {
			throw locked(account.getLockedUntil());
		}
		// Always check the password, even for an unknown or inactive account: skipping BCrypt on those
		// branches would make them measurably faster than a wrong password on a real, active account,
		// leaking through timing exactly the distinction this login answers the same code for.
		var hashToCheck = account != null ? account.getPasswordHash() : dummyPasswordHash;
		var passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);
		if (account == null || !account.isActive() || !passwordMatches) {
			if (account != null) {
				// The row count is ignored: whether or not the bookkeeping update finds the row (e.g. the
				// account was deleted concurrently) does not change the outcome — login is rejected either way.
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
