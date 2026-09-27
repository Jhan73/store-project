package com.jhanantezana.jugueria.store.internal;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.store.OpeningHourSnapshot;
import com.jhanantezana.jugueria.store.StoreError;

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
@Table(schema = "store", name = "opening_hour")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpeningHour extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(nullable = false)
	private boolean closed;

	@Column(name = "opens_at")
	private LocalTime opensAt;

	@Column(name = "closes_at")
	private LocalTime closesAt;

	@Version
	private long version;

	public OpeningHour(DayOfWeek dayOfWeek, boolean closed, @Nullable LocalTime opensAt,
			@Nullable LocalTime closesAt) {
		validate(closed, opensAt, closesAt);
		this.dayOfWeek = dayOfWeek;
		this.closed = closed;
		this.opensAt = closed ? null : opensAt;
		this.closesAt = closed ? null : closesAt;
	}

	public void change(boolean closed, @Nullable LocalTime opensAt, @Nullable LocalTime closesAt) {
		validate(closed, opensAt, closesAt);
		this.closed = closed;
		this.opensAt = closed ? null : opensAt;
		this.closesAt = closed ? null : closesAt;
	}

	public OpeningHourSnapshot snapshot() {
		return new OpeningHourSnapshot(dayOfWeek, closed, opensAt, closesAt);
	}

	private static void validate(boolean closed, @Nullable LocalTime opensAt, @Nullable LocalTime closesAt) {
		if (closed) {
			return;
		}
		if (opensAt == null || closesAt == null) {
			throw invalid("An open day requires both opensAt and closesAt");
		}
		if (opensAt.equals(closesAt)) {
			throw invalid("opensAt and closesAt must differ");
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(StoreError.INVALID_OPENING_HOURS, detail);
	}

}
