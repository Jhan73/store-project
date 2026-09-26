package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.IdentityError;
import com.jhanantezana.jugueria.identity.UserCreated;
import com.jhanantezana.jugueria.identity.UserDeactivated;
import com.jhanantezana.jugueria.identity.UserRoleChanged;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Role;

// ADMIN-only staff administration: create, list, get, role change, deactivate, reactivate (PRD FR-ADM-01).
@Service
public class StaffAccountService {

	private final UserAccountRepository accounts;

	private final SetPasswordTokenService setPasswordTokens;

	private final RefreshTokenService refreshTokens;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	StaffAccountService(UserAccountRepository accounts, SetPasswordTokenService setPasswordTokens,
			RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.accounts = accounts;
		this.setPasswordTokens = setPasswordTokens;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	// The account starts with no usable password: this hash is never given to anyone (owner decision).
	@Transactional
	StaffProvisioned createAccountAndToken(String email, Role role) {
		if (role == Role.CUSTOMER) {
			throw invalidStaffRole();
		}
		var normalizedEmail = normalize(email);
		var now = Instant.now(clock);
		UserAccount account;
		try {
			account = accounts.saveAndFlush(
					new UserAccount(normalizedEmail, passwordEncoder.encode(RefreshTokens.newRawToken()), role, now));
		}
		catch (DataIntegrityViolationException e) {
			throw emailAlreadyRegistered();
		}
		var issued = setPasswordTokens.issue(account.getId());
		events.publishEvent(
				new UserCreated(account.getId(), normalizedEmail, role, currentActor.id(), currentActor.role(), now));
		return new StaffProvisioned(account, issued.rawToken(), issued.expiresAt());
	}

	@Transactional(readOnly = true)
	public Page<UserAccount> list(Pageable pageable) {
		return accounts.findByRoleNot(Role.CUSTOMER, pageable);
	}

	@Transactional(readOnly = true)
	public UserAccount get(UUID id) {
		return findStaffOrThrow(id);
	}

	@Transactional
	public UserAccount changeRole(UUID id, Role newRole) {
		if (newRole == Role.CUSTOMER) {
			throw invalidStaffRole();
		}
		rejectSelf(id);
		var account = findStaffOrThrow(id);
		var oldRole = account.getRole();
		if (oldRole == newRole) {
			return account;
		}
		if (oldRole == Role.ADMIN && isLastActiveAdmin()) {
			throw lastActiveAdminRequired();
		}
		var now = Instant.now(clock);
		account.changeRole(newRole, now);
		refreshTokens.revokeAllForUser(id);
		events.publishEvent(new UserRoleChanged(id, oldRole, newRole, currentActor.id(), currentActor.role(), now));
		return account;
	}

	@Transactional
	public UserAccount deactivate(UUID id) {
		rejectSelf(id);
		var account = findStaffOrThrow(id);
		if (!account.isActive()) {
			return account;
		}
		if (account.getRole() == Role.ADMIN && isLastActiveAdmin()) {
			throw lastActiveAdminRequired();
		}
		var now = Instant.now(clock);
		account.deactivate(now);
		refreshTokens.revokeAllForUser(id);
		setPasswordTokens.revokeAllUnused(id);
		events.publishEvent(new UserDeactivated(id, currentActor.id(), currentActor.role(), now));
		return account;
	}

	// No dedicated event: the tech-spec lists only UserCreated, UserDeactivated, and UserRoleChanged for identity.
	@Transactional
	public UserAccount reactivate(UUID id) {
		var account = findStaffOrThrow(id);
		if (account.isActive()) {
			return account;
		}
		account.reactivate(Instant.now(clock));
		return account;
	}

	private UserAccount findStaffOrThrow(UUID id) {
		var account = accounts.findById(id).orElseThrow(StaffAccountService::notFound);
		if (account.getRole() == Role.CUSTOMER) {
			throw notFound();
		}
		return account;
	}

	private void rejectSelf(UUID id) {
		var actorId = currentActor.id();
		if (actorId != null && actorId.equals(id)) {
			throw cannotModifyOwnAccount();
		}
	}

	private boolean isLastActiveAdmin() {
		return accounts.countByRoleAndActiveTrue(Role.ADMIN) <= 1;
	}

	private static String normalize(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static BusinessException invalidStaffRole() {
		return new BusinessException(IdentityError.INVALID_STAFF_ROLE, "CUSTOMER is not a staff role");
	}

	private static BusinessException emailAlreadyRegistered() {
		return new BusinessException(IdentityError.EMAIL_ALREADY_REGISTERED, "Email is already registered");
	}

	private static BusinessException cannotModifyOwnAccount() {
		return new BusinessException(IdentityError.CANNOT_MODIFY_OWN_ACCOUNT,
				"An admin cannot deactivate or change the role of their own account");
	}

	private static BusinessException lastActiveAdminRequired() {
		return new BusinessException(IdentityError.LAST_ACTIVE_ADMIN_REQUIRED,
				"At least one active ADMIN account is required");
	}

	private static BusinessException notFound() {
		return new BusinessException(CommonError.NOT_FOUND, "Staff account not found");
	}

}
