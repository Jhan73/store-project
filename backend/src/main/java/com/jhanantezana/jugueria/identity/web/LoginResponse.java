package com.jhanantezana.jugueria.identity.web;

import java.util.UUID;

import com.jhanantezana.jugueria.identity.internal.LoginResult;
import com.jhanantezana.jugueria.shared.Role;

record LoginResponse(String accessToken, String tokenType, UUID userId, Role role) {

	static LoginResponse from(LoginResult result) {
		return new LoginResponse(result.accessToken(), "Bearer", result.userId(), result.role());
	}

}
