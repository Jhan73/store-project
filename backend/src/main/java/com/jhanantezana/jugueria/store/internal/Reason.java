package com.jhanantezana.jugueria.store.internal;

import java.time.Instant;

import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.store.ReasonType;

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
@Table(schema = "store", name = "reason")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reason extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReasonType type;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public Reason(ReasonType type, String code, Instant now) {
		this.type = type;
		this.code = code;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void deactivate(Instant now) {
		this.active = false;
		this.updatedAt = now;
	}

	public void reactivate(Instant now) {
		this.active = true;
		this.updatedAt = now;
	}

}
