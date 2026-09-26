package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.AuthError;
import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Ids;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository tokens;

	private final UserAccountRepository accounts;

	private final AccessTokenIssuer accessTokens;

	private final IdentityProperties.RefreshToken properties;

	private final Clock clock;

	RefreshTokenService(RefreshTokenRepository tokens, UserAccountRepository accounts, AccessTokenIssuer accessTokens,
			IdentityProperties properties, Clock clock) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.accessTokens = accessTokens;
		this.properties = properties.refreshToken();
		this.clock = clock;
	}

	@Transactional
	public IssuedRefreshToken issueFamily(UUID userId) {
		return issue(Ids.newId(), userId);
	}

	// The revocation triggered by a rejection must survive that same rejection.
	@Transactional(noRollbackFor = BusinessException.class)
	public RefreshResult rotate(@Nullable String rawToken) {
		if (rawToken == null) {
			throw invalidRefreshToken();
		}
		var now = Instant.now(clock);
		var token = tokens.findByTokenHash(RefreshTokens.hash(rawToken)).orElseThrow(RefreshTokenService::invalidRefreshToken);
		if (token.getRevokedAt() != null || token.isExpired(now)) {
			throw invalidRefreshToken();
		}
		if (token.getUsedAt() != null) {
			// Presented after its own rotation: the family is compromised.
			tokens.revokeFamily(token.getFamilyId(), now);
			throw invalidRefreshToken();
		}
		var account = accounts.findById(token.getUserId()).orElseThrow(RefreshTokenService::invalidRefreshToken);
		if (!account.isActive() || account.isLocked(now)) {
			throw invalidRefreshToken();
		}
		if (tokens.markUsed(token.getId(), now) == 0) {
			// Lost the race: a concurrent request rotated this token first.
			tokens.revokeFamily(token.getFamilyId(), now);
			throw invalidRefreshToken();
		}
		var issued = issue(token.getFamilyId(), account.getId());
		return new RefreshResult(accessTokens.issue(account.getId(), account.getRole()), issued.rawToken(),
				issued.expiresAt(), account.getId(), account.getRole());
	}

	@Transactional
	public void logout(@Nullable String rawToken) {
		if (rawToken == null) {
			return;
		}
		tokens.findByTokenHash(RefreshTokens.hash(rawToken))
			.ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
	}

	// Slice 4 (deactivation/role change) calls this so every active session ends immediately.
	@Transactional
	public void revokeAllForUser(UUID userId) {
		tokens.revokeAllForUser(userId, Instant.now(clock));
	}

	private IssuedRefreshToken issue(UUID familyId, UUID userId) {
		var now = Instant.now(clock);
		var expiresAt = now.plus(properties.ttl());
		var raw = RefreshTokens.newRawToken();
		tokens.save(new RefreshToken(familyId, userId, RefreshTokens.hash(raw), now, expiresAt));
		return new IssuedRefreshToken(raw, expiresAt);
	}

	private static BusinessException invalidRefreshToken() {
		return new BusinessException(AuthError.INVALID_REFRESH_TOKEN, "Invalid or expired refresh token");
	}

}
