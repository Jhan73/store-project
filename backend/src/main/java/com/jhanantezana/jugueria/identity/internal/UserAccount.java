package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;

import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "identity", name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends BaseEntity {

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	// Set from the injected Clock at construction, never a Hibernate-generated timestamp: every other
	// persisted moment in this codebase comes from Clock, and a generator would be untestable and
	// silently out of step with the failed-attempt bookkeeping, which also stamps updatedAt itself.
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public UserAccount(String email, String passwordHash, Role role, Instant now) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UserAccount(String email, String passwordHash, Role role, Instant now, boolean active) {
		this(email, passwordHash, role, now);
		this.active = active;
	}

	// Only for tests (identity.internal and identity.web ITs) that need a locked fixture without going
	// through the repository's atomic update; public because those ITs live in different sub-packages.
	public UserAccount(String email, String passwordHash, Role role, Instant now, int failedAttempts,
			Instant lockedUntil) {
		this(email, passwordHash, role, now);
		this.failedAttempts = failedAttempts;
		this.lockedUntil = lockedUntil;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

}
