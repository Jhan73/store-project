package com.jhanantezana.jugueria.identity.internal;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.AccountActivity;

@Service
class UserAccountActivity implements AccountActivity {

	private final UserAccountRepository accounts;

	UserAccountActivity(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isActive(UUID userId) {
		return accounts.existsByIdAndActiveTrue(userId);
	}

}
