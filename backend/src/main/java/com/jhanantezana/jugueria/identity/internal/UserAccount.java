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

	// Stamped from the injected Clock, never by a Hibernate generator.
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

	// Test fixtures only; public because the ITs live in other sub-packages.
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
