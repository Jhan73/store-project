package com.jhanantezana.jugueria.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.shared.Role;

@Service
class IdentityService implements IdentityApi {

	private final UserAccountRepository accounts;

	IdentityService(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> activeAdminEmails() {
		return accounts.findByRoleAndActiveTrueOrderByEmail(Role.ADMIN).stream().map(UserAccount::getEmail).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> emailOf(UUID userId) {
		return accounts.findById(userId).map(UserAccount::getEmail);
	}

}
