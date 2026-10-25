package com.jhanantezana.jugueria.identity.web;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.identity.internal.LoginResult;
import com.jhanantezana.jugueria.identity.internal.RefreshResult;
import com.jhanantezana.jugueria.shared.Role;

// sessionExpiresAt: when the refresh token family ends, so a screen that stays on can warn before it falls to login.
record LoginResponse(String accessToken, String tokenType, UUID userId, Role role, Instant sessionExpiresAt) {

	static LoginResponse from(LoginResult result) {
		return new LoginResponse(result.accessToken(), "Bearer", result.userId(), result.role(),
				result.sessionExpiresAt());
	}

	static LoginResponse from(RefreshResult result) {
		return new LoginResponse(result.accessToken(), "Bearer", result.userId(), result.role(),
				result.sessionExpiresAt());
	}

}
