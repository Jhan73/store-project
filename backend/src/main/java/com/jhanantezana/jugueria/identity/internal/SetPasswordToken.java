package com.jhanantezana.jugueria.identity.internal;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "identity", name = "set_password_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetPasswordToken extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	public SetPasswordToken(UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

}
