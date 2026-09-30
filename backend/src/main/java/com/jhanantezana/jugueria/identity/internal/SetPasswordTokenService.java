package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;

@Service
class SetPasswordTokenService {

	private final SetPasswordTokenRepository tokens;

	private final IdentityProperties.SetPassword properties;

	private final Clock clock;

	SetPasswordTokenService(SetPasswordTokenRepository tokens, IdentityProperties properties, Clock clock) {
		this.tokens = tokens;
		this.properties = properties.setPassword();
		this.clock = clock;
	}

	// A new token invalidates every earlier unused one for the same user.
	@Transactional
	IssuedSetPasswordToken issue(UUID userId) {
		var now = Instant.now(clock);
		tokens.revokeAllUnusedForUser(userId, now);
		var raw = RefreshTokens.newRawToken();
		var expiresAt = now.plus(properties.tokenTtl());
		tokens.save(new SetPasswordToken(userId, RefreshTokens.hash(raw), now, expiresAt));
		return new IssuedSetPasswordToken(raw, expiresAt);
	}

	@Transactional
	void revokeAllUnused(UUID userId) {
		tokens.revokeAllUnusedForUser(userId, Instant.now(clock));
	}

}
