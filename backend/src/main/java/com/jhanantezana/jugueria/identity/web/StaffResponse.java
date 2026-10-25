package com.jhanantezana.jugueria.identity.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.shared.Role;

record StaffResponse(UUID id, @Nullable String username, String email, Role role, boolean active,
		boolean deletable, Instant createdAt) {

	static StaffResponse from(UserAccount account, boolean deletable) {
		return new StaffResponse(account.getId(), account.getUsername(), account.getEmail(), account.getRole(),
				account.isActive(), deletable, account.getCreatedAt());
	}

}
