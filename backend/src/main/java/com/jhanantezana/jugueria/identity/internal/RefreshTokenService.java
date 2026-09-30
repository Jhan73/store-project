package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Duration;
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
		var now = Instant.now(clock);
		return issue(Ids.newId(), userId, now, now);
	}

	// The revocation triggered by a rejection must survive that same rejection.
	@Transactional(noRollbackFor = BusinessException.class)
	public RefreshResult rotate(@Nullable String rawToken) {
		if (rawToken == null) {
			throw invalidRefreshToken();
		}
		var now = Instant.now(clock);
		var token = tokens.findByTokenHash(RefreshTokens.hash(rawToken)).orElseThrow(RefreshTokenService::invalidRefreshToken);
		if (token.getRevokedAt() != null || token.isExpired(now) || token.exceedsAbsoluteLifetime(now, properties.absoluteTtl())) {
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
		var absoluteFloor = now.minus(properties.absoluteTtl());
		if (tokens.markUsed(token.getId(), now, absoluteFloor) == 0) {
			// Lost the race, or the absolute lifetime lapsed between the checks above and this update.
			tokens.revokeFamily(token.getFamilyId(), now);
			throw invalidRefreshToken();
		}
		var issued = issue(token.getFamilyId(), account.getId(), token.getFamilyStartedAt(), now);
		return new RefreshResult(accessTokens.issue(account.getId(), account.getRole()), issued.rawToken(),
				issued.expiresAt(), issued.maxAge(), account.getId(), account.getRole());
	}

	@Transactional
	public void logout(@Nullable String rawToken) {
		if (rawToken == null) {
			return;
		}
		tokens.findByTokenHash(RefreshTokens.hash(rawToken))
			.ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
	}

	@Transactional
	public void revokeAllForUser(UUID userId) {
		tokens.revokeAllForUser(userId, Instant.now(clock));
	}

	private IssuedRefreshToken issue(UUID familyId, UUID userId, Instant familyStartedAt, Instant now) {
		var idleExpiry = now.plus(properties.idleTtl());
		var absoluteExpiry = familyStartedAt.plus(properties.absoluteTtl());
		var expiresAt = idleExpiry.isBefore(absoluteExpiry) ? idleExpiry : absoluteExpiry;
		var raw = RefreshTokens.newRawToken();
		tokens.save(new RefreshToken(familyId, userId, RefreshTokens.hash(raw), now, expiresAt, familyStartedAt));
		return new IssuedRefreshToken(raw, expiresAt, Duration.between(now, expiresAt));
	}

	private static BusinessException invalidRefreshToken() {
		return new BusinessException(AuthError.INVALID_REFRESH_TOKEN, "Invalid or expired refresh token");
	}

}
