package com.jhanantezana.jugueria.catalog.internal;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.catalog.CategorySnapshot;
import com.jhanantezana.jugueria.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "catalog", name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

	@Column(name = "station_id", nullable = false)
	private UUID stationId;

	@Column(nullable = false)
	private String name;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public Category(String name, UUID stationId, int displayOrder, Instant now) {
		this.name = name;
		this.stationId = stationId;
		this.displayOrder = displayOrder;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void change(String name, UUID stationId, int displayOrder, Instant now) {
		this.name = name;
		this.stationId = stationId;
		this.displayOrder = displayOrder;
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

	public CategorySnapshot snapshot() {
		return new CategorySnapshot(name, displayOrder, stationId, active);
	}

}
