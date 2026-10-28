package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;

import com.jhanantezana.jugueria.instore.WalletProviderSnapshot;
import com.jhanantezana.jugueria.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "instore", name = "wallet_provider")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletProvider extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public WalletProvider(String name, Instant now) {
		this.name = name.strip();
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void rename(String name, Instant now) {
		this.name = name.strip();
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

	public WalletProviderSnapshot snapshot() {
		return new WalletProviderSnapshot(name, active);
	}

}
