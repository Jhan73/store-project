package com.jhanantezana.jugueria.identity.web;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.shared.Role;

record StaffResponse(UUID id, String email, Role role, boolean active, Instant createdAt) {

	static StaffResponse from(UserAccount account) {
		return new StaffResponse(account.getId(), account.getEmail(), account.getRole(), account.isActive(),
				account.getCreatedAt());
	}

}
