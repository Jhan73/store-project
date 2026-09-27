package com.jhanantezana.jugueria.store.internal;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.StoreError;
import com.jhanantezana.jugueria.store.StoreSettingsSnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "store", name = "store_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreSettings extends BaseEntity {

	@Column(name = "time_zone", nullable = false)
	private String timeZone;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private Currency currency;

	@Column(name = "base_prep_minutes", nullable = false)
	private int basePrepMinutes;

	@Column(name = "queue_minutes_per_order", nullable = false)
	private int queueMinutesPerOrder;

	@Column(name = "busy_mode_minutes", nullable = false)
	private int busyModeMinutes;

	@Column(name = "board_warning_minutes", nullable = false)
	private int boardWarningMinutes;

	@Column(name = "board_late_minutes", nullable = false)
	private int boardLateMinutes;

	private Money registerDifferenceThreshold;

	@Column(name = "exception_threshold", nullable = false)
	private int exceptionThreshold;

	@Column(name = "online_capacity_limit", nullable = false)
	private int onlineCapacityLimit;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	// Test fixtures only; the real singleton row is seeded by migration, never created by the app.
	public StoreSettings(String timeZone, Currency currency, int basePrepMinutes, int queueMinutesPerOrder,
			int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes, Money registerDifferenceThreshold,
			int exceptionThreshold, int onlineCapacityLimit, Instant now) {
		validate(timeZone, currency, basePrepMinutes, queueMinutesPerOrder, busyModeMinutes, boardWarningMinutes,
				boardLateMinutes, registerDifferenceThreshold, exceptionThreshold, onlineCapacityLimit);
		this.timeZone = timeZone;
		this.currency = currency;
		this.basePrepMinutes = basePrepMinutes;
		this.queueMinutesPerOrder = queueMinutesPerOrder;
		this.busyModeMinutes = busyModeMinutes;
		this.boardWarningMinutes = boardWarningMinutes;
		this.boardLateMinutes = boardLateMinutes;
		this.registerDifferenceThreshold = registerDifferenceThreshold;
		this.exceptionThreshold = exceptionThreshold;
		this.onlineCapacityLimit = onlineCapacityLimit;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String timeZone, Currency currency, int basePrepMinutes, int queueMinutesPerOrder,
			int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes, Money registerDifferenceThreshold,
			int exceptionThreshold, int onlineCapacityLimit, Instant now) {
		validate(timeZone, currency, basePrepMinutes, queueMinutesPerOrder, busyModeMinutes, boardWarningMinutes,
				boardLateMinutes, registerDifferenceThreshold, exceptionThreshold, onlineCapacityLimit);
		this.timeZone = timeZone;
		this.currency = currency;
		this.basePrepMinutes = basePrepMinutes;
		this.queueMinutesPerOrder = queueMinutesPerOrder;
		this.busyModeMinutes = busyModeMinutes;
		this.boardWarningMinutes = boardWarningMinutes;
		this.boardLateMinutes = boardLateMinutes;
		this.registerDifferenceThreshold = registerDifferenceThreshold;
		this.exceptionThreshold = exceptionThreshold;
		this.onlineCapacityLimit = onlineCapacityLimit;
		this.updatedAt = now;
	}

	public StoreSettingsSnapshot snapshot() {
		return new StoreSettingsSnapshot(timeZone, currency, basePrepMinutes, queueMinutesPerOrder, busyModeMinutes,
				boardWarningMinutes, boardLateMinutes, registerDifferenceThreshold, exceptionThreshold,
				onlineCapacityLimit);
	}

	public ZoneId zoneId() {
		return ZoneId.of(timeZone);
	}

	private static void validate(String timeZone, Currency currency, int basePrepMinutes,
			int queueMinutesPerOrder, int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes,
			Money registerDifferenceThreshold, int exceptionThreshold, int onlineCapacityLimit) {
		try {
			ZoneId.of(timeZone);
		}
		catch (DateTimeException e) {
			throw invalid("timeZone is not a valid IANA zone id: " + timeZone);
		}
		if (basePrepMinutes <= 0 || queueMinutesPerOrder < 0 || busyModeMinutes < 0 || boardWarningMinutes <= 0
				|| exceptionThreshold <= 0 || onlineCapacityLimit <= 0) {
			throw invalid("Minutes and thresholds must be positive (queue/busy minutes may be zero)");
		}
		if (registerDifferenceThreshold.isNegative()) {
			throw invalid("registerDifferenceThreshold must not be negative");
		}
		if (!registerDifferenceThreshold.currency().equals(currency)) {
			throw invalid("registerDifferenceThreshold currency must match the store currency");
		}
		if (boardLateMinutes <= boardWarningMinutes) {
			throw new BusinessException(StoreError.INVALID_BOARD_THRESHOLDS,
					"boardLateMinutes must be greater than boardWarningMinutes");
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(StoreError.INVALID_SETTINGS_VALUE, detail);
	}

}
