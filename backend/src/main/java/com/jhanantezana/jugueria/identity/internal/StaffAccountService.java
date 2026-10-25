package com.jhanantezana.jugueria.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.IdentityError;
import com.jhanantezana.jugueria.identity.SetPasswordLinkReissued;
import com.jhanantezana.jugueria.identity.UserCreated;
import com.jhanantezana.jugueria.identity.UserDeactivated;
import com.jhanantezana.jugueria.identity.UserDeleted;
import com.jhanantezana.jugueria.identity.UserReactivated;
import com.jhanantezana.jugueria.identity.UserRenamed;
import com.jhanantezana.jugueria.identity.UserRoleChanged;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.shared.StaffChangeGuard;

@Service
public class StaffAccountService {

	private final UserAccountRepository accounts;

	private final SetPasswordTokenService setPasswordTokens;

	private final SetPasswordTokenRepository setPasswordTokenRows;

	private final RefreshTokenRepository refreshTokenRows;

	private final RefreshTokenService refreshTokens;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final ActiveAdminLock activeAdminLock;

	private final List<StaffChangeGuard> changeGuards;

	private final Clock clock;

	StaffAccountService(UserAccountRepository accounts, SetPasswordTokenService setPasswordTokens,
			SetPasswordTokenRepository setPasswordTokenRows, RefreshTokenRepository refreshTokenRows,
			RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
			CurrentActor currentActor, ActiveAdminLock activeAdminLock, List<StaffChangeGuard> changeGuards,
			Clock clock) {
		this.accounts = accounts;
		this.setPasswordTokens = setPasswordTokens;
		this.setPasswordTokenRows = setPasswordTokenRows;
		this.refreshTokenRows = refreshTokenRows;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
		this.currentActor = currentActor;
		this.activeAdminLock = activeAdminLock;
		this.changeGuards = changeGuards;
		this.clock = clock;
	}

	// The account starts with no usable password: this random hash is never given to anyone.
	@Transactional
	StaffProvisioned createAccountAndToken(String username, String email, Role role) {
		if (role == Role.CUSTOMER) {
			throw invalidStaffRole();
		}
		return createAccount(username.strip(), normalize(email), role, Instant.now(clock));
	}

	// Checking "no active admin yet" and creating one must be one atomic decision, or two concurrent runs both pass.
	@Transactional
	Optional<StaffProvisioned> createFirstAdminAccountAndToken(String email) {
		activeAdminLock.acquire();
		if (accounts.countByRoleAndActiveTrue(Role.ADMIN) > 0) {
			return Optional.empty();
		}
		return Optional.of(createAccount(null, normalize(email), Role.ADMIN, Instant.now(clock)));
	}

	// An admin resending a link for someone stuck without a working password (missed link, reactivated account).
	@Transactional
	StaffProvisioned reissueSetPasswordToken(UUID id) {
		rejectSelf(id);
		var account = findStaffOrThrow(id);
		var issued = setPasswordTokens.issue(account.getId());
		var now = Instant.now(clock);
		events.publishEvent(new SetPasswordLinkReissued(account.getId(), currentActor.id(), currentActor.role(), now));
		return new StaffProvisioned(account, issued.rawToken(), issued.expiresAt());
	}

	private StaffProvisioned createAccount(@Nullable String username, String normalizedEmail, Role role,
			Instant now) {
		var newAccount = new UserAccount(normalizedEmail, passwordEncoder.encode(RefreshTokens.newRawToken()), role,
				now);
		newAccount.rename(username, now);
		UserAccount account;
		try {
			account = accounts.saveAndFlush(newAccount);
		}
		catch (DataIntegrityViolationException e) {
			throw isUsernameViolation(e) ? usernameAlreadyTaken() : emailAlreadyRegistered();
		}
		var issued = setPasswordTokens.issue(account.getId());
		events.publishEvent(
				new UserCreated(account.getId(), normalizedEmail, username, role, currentActor.id(), currentActor.role(), now));
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

	// Self-rename is allowed: the bootstrap admin has no username and names themselves from the staff list.
	@Transactional
	public UserAccount rename(UUID id, String username) {
		var account = findStaffOrThrow(id);
		var newUsername = username.strip();
		var oldUsername = account.getUsername();
		if (newUsername.equals(oldUsername)) {
			return account;
		}
		var now = Instant.now(clock);
		account.rename(newUsername, now);
		flushUsernameChange(() -> accounts.saveAndFlush(account));
		events.publishEvent(new UserRenamed(id, oldUsername, newUsername, currentActor.id(), currentActor.role(), now));
		return account;
	}

	@Transactional
	public UserAccount changeRole(UUID id, Role newRole) {
		activeAdminLock.acquire();
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
		changeGuards.forEach(guard -> guard.requireCanLeaveRole(id));
		var now = Instant.now(clock);
		account.changeRole(newRole, now);
		refreshTokens.revokeAllForUser(id);
		events.publishEvent(new UserRoleChanged(id, oldRole, newRole, currentActor.id(), currentActor.role(), now));
		return account;
	}

	@Transactional
	public UserAccount deactivate(UUID id) {
		activeAdminLock.acquire();
		rejectSelf(id);
		var account = findStaffOrThrow(id);
		if (!account.isActive()) {
			return account;
		}
		if (account.getRole() == Role.ADMIN && isLastActiveAdmin()) {
			throw lastActiveAdminRequired();
		}
		changeGuards.forEach(guard -> guard.requireCanLeaveRole(id));
		var now = Instant.now(clock);
		account.deactivate(now);
		refreshTokens.revokeAllForUser(id);
		setPasswordTokens.revokeAllUnused(id);
		events.publishEvent(new UserDeactivated(id, currentActor.id(), currentActor.role(), now));
		return account;
	}

	/**
	 * The given accounts an admin may delete: invitations nobody accepted (no consumed set-password link and no
	 * session ever), that are not the actor's own and not the last active admin.
	 */
	@Transactional(readOnly = true)
	public Set<UUID> deletable(Collection<UserAccount> candidates) {
		if (candidates.isEmpty()) {
			return Set.of();
		}
		var ids = candidates.stream().map(UserAccount::getId).toList();
		var used = new HashSet<>(setPasswordTokenRows.userIdsWithUsedToken(ids));
		used.addAll(refreshTokenRows.userIdsWithTokens(ids));
		var lastAdmin = accounts.countByRoleAndActiveTrue(Role.ADMIN) <= 1;
		var actorId = currentActor.id();
		return candidates.stream()
			.filter(account -> !used.contains(account.getId()))
			.filter(account -> !account.getId().equals(actorId))
			.filter(account -> !(lastAdmin && account.getRole() == Role.ADMIN && account.isActive()))
			.map(UserAccount::getId)
			.collect(Collectors.toSet());
	}

	// Account row first, then its tokens: setPassword takes them in the same order, and holding the row keeps a link
	// from being consumed between the check and the delete.
	@Transactional
	public void delete(UUID id) {
		activeAdminLock.acquire();
		rejectSelf(id);
		var account = accounts.findForUpdateById(id).orElseThrow(StaffAccountService::notFound);
		if (account.getRole() == Role.CUSTOMER) {
			throw notFound();
		}
		var everActive = !setPasswordTokenRows.userIdsWithUsedToken(List.of(id)).isEmpty()
				|| !refreshTokenRows.userIdsWithTokens(List.of(id)).isEmpty();
		if (everActive) {
			throw new BusinessException(IdentityError.STAFF_ACCOUNT_IN_USE,
					"The account has signed in or set its password; deactivate it instead");
		}
		if (account.getRole() == Role.ADMIN && account.isActive() && isLastActiveAdmin()) {
			throw lastActiveAdminRequired();
		}
		var now = Instant.now(clock);
		setPasswordTokenRows.deleteAllForUser(id);
		accounts.delete(account);
		accounts.flush();
		events.publishEvent(new UserDeleted(id, account.getUsername(), account.getRole(), account.isActive(),
				currentActor.id(), currentActor.role(), now));
	}

	@Transactional
	public UserAccount reactivate(UUID id) {
		var account = findStaffOrThrow(id);
		if (account.isActive()) {
			return account;
		}
		var now = Instant.now(clock);
		account.reactivate(now);
		flushUsernameChange(accounts::flush);
		events.publishEvent(new UserReactivated(id, currentActor.id(), currentActor.role(), now));
		return account;
	}

	private static void flushUsernameChange(Runnable flush) {
		try {
			flush.run();
		}
		catch (DataIntegrityViolationException e) {
			throw isUsernameViolation(e) ? usernameAlreadyTaken() : e;
		}
	}

	private static boolean isUsernameViolation(DataIntegrityViolationException e) {
		return Constraints.violated(e, Constraints.USERNAME);
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

	private static BusinessException usernameAlreadyTaken() {
		return new BusinessException(IdentityError.USERNAME_ALREADY_TAKEN, "Username is already taken");
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
