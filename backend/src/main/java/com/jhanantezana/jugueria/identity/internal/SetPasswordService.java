package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.AuthError;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.BusinessException;

@Service
public class SetPasswordService {

	private final SetPasswordTokenRepository tokens;

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	SetPasswordService(SetPasswordTokenRepository tokens, UserAccountRepository accounts,
			PasswordEncoder passwordEncoder, Clock clock) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	// Account state is checked before consuming the token, so an incidental rejection never burns a valid one.
	@Transactional
	public void setPassword(@Nullable String rawToken, String newPassword) {
		if (rawToken == null) {
			throw invalidToken();
		}
		var now = Instant.now(clock);
		var token = tokens.findByTokenHash(RefreshTokens.hash(rawToken)).orElseThrow(SetPasswordService::invalidToken);
		var account = accounts.findById(token.getUserId()).orElseThrow(SetPasswordService::invalidToken);
		if (!account.isActive()) {
			throw invalidToken();
		}
		if (tokens.markUsed(token.getId(), now) == 0) {
			throw invalidToken();
		}
		account.changePassword(passwordEncoder.encode(newPassword), now);
	}

	private static BusinessException invalidToken() {
		return new BusinessException(AuthError.INVALID_SET_PASSWORD_TOKEN, "Invalid or expired set-password token");
	}

}
