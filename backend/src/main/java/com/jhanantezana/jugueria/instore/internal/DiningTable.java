package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "instore", name = "dining_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiningTable extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column
	private @Nullable String area;

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

	public DiningTable(String name, @Nullable String area, int displayOrder, Instant now) {
		this.name = name.strip();
		this.area = normalized(area);
		this.displayOrder = displayOrder;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void change(String name, @Nullable String area, int displayOrder, Instant now) {
		this.name = name.strip();
		this.area = normalized(area);
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

	private static @Nullable String normalized(@Nullable String area) {
		return area == null || area.isBlank() ? null : area.strip();
	}

}
