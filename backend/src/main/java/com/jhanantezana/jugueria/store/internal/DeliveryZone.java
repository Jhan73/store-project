package com.jhanantezana.jugueria.store.internal;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.DeliveryZoneSnapshot;
import com.jhanantezana.jugueria.store.StoreError;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "store", name = "delivery_zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryZone extends BaseEntity {

	@Column(nullable = false)
	private String name;

	private Money fee;

	@Column(name = "delivery_minutes", nullable = false)
	private int deliveryMinutes;

	private Money minimumOrder;

	private Money freeDeliveryThreshold;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public DeliveryZone(String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
			@Nullable Money freeDeliveryThreshold, Instant now) {
		validate(fee, deliveryMinutes, minimumOrder, freeDeliveryThreshold);
		this.name = name;
		this.fee = fee;
		this.deliveryMinutes = deliveryMinutes;
		this.minimumOrder = minimumOrder;
		this.freeDeliveryThreshold = freeDeliveryThreshold;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void change(String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
			@Nullable Money freeDeliveryThreshold, Instant now) {
		validate(fee, deliveryMinutes, minimumOrder, freeDeliveryThreshold);
		this.name = name;
		this.fee = fee;
		this.deliveryMinutes = deliveryMinutes;
		this.minimumOrder = minimumOrder;
		this.freeDeliveryThreshold = freeDeliveryThreshold;
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

	public DeliveryZoneSnapshot snapshot() {
		return new DeliveryZoneSnapshot(name, fee, deliveryMinutes, minimumOrder, freeDeliveryThreshold);
	}

	private static void validate(Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
			@Nullable Money freeDeliveryThreshold) {
		if (fee.isNegative()) {
			throw invalid("fee must not be negative");
		}
		if (deliveryMinutes <= 0) {
			throw invalid("deliveryMinutes must be positive");
		}
		if (minimumOrder != null && minimumOrder.isNegative()) {
			throw invalid("minimumOrder must not be negative");
		}
		if (freeDeliveryThreshold != null && freeDeliveryThreshold.isNegative()) {
			throw invalid("freeDeliveryThreshold must not be negative");
		}
		if (minimumOrder != null && freeDeliveryThreshold != null
				&& freeDeliveryThreshold.compareTo(minimumOrder) < 0) {
			throw invalid("freeDeliveryThreshold must be at least the minimumOrder");
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(StoreError.INVALID_DELIVERY_ZONE, detail);
	}

}
