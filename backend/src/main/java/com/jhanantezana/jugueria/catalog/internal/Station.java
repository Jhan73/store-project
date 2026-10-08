package com.jhanantezana.jugueria.catalog.internal;

import java.time.Instant;

import com.jhanantezana.jugueria.catalog.StationSnapshot;
import com.jhanantezana.jugueria.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "catalog", name = "station")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Station extends BaseEntity {

	@Column(nullable = false)
	private String name;

	// Only the seeded station is the default; the flag is never rewritten from Java.
	@Column(name = "default_station", nullable = false, updatable = false)
	private boolean defaultStation;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public Station(String name, Instant now) {
		this.name = name;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void rename(String name, Instant now) {
		this.name = name;
		this.updatedAt = now;
	}

	public StationSnapshot snapshot() {
		return new StationSnapshot(name);
	}

}
