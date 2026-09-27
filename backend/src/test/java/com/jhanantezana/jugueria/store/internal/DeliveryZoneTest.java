package com.jhanantezana.jugueria.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.StoreError;

class DeliveryZoneTest {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void createsAZoneWithoutOptionalThresholds() {
		var zone = new DeliveryZone("Downtown", Money.of("5.00", PEN), 20, null, null, NOW);

		assertThat(zone.getName()).isEqualTo("Downtown");
		assertThat(zone.getMinimumOrder()).isNull();
		assertThat(zone.isActive()).isTrue();
	}

	@Test
	void rejectsANegativeFee() {
		var negative = new Money(BigDecimal.valueOf(-1), PEN);

		assertThatThrownBy(() -> new DeliveryZone("Downtown", negative, 20, null, null, NOW))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_DELIVERY_ZONE);
	}

	@Test
	void rejectsANonPositiveDeliveryMinutes() {
		assertThatThrownBy(() -> new DeliveryZone("Downtown", Money.of("5.00", PEN), 0, null, null, NOW))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_DELIVERY_ZONE);
	}

	@Test
	void rejectsAFreeDeliveryThresholdBelowTheMinimumOrder() {
		assertThatThrownBy(() -> new DeliveryZone("Downtown", Money.of("5.00", PEN), 20, Money.of("30.00", PEN),
				Money.of("20.00", PEN), NOW)).isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).errorCode())
			.isEqualTo(StoreError.INVALID_DELIVERY_ZONE);
	}

	@Test
	void deactivateAndReactivateToggleActive() {
		var zone = new DeliveryZone("Downtown", Money.of("5.00", PEN), 20, null, null, NOW);

		zone.deactivate(NOW.plusSeconds(60));
		assertThat(zone.isActive()).isFalse();

		zone.reactivate(NOW.plusSeconds(120));
		assertThat(zone.isActive()).isTrue();
	}

	@Test
	void changeAppliesNewFields() {
		var zone = new DeliveryZone("Downtown", Money.of("5.00", PEN), 20, null, null, NOW);
		var later = NOW.plusSeconds(60);

		zone.change("Uptown", Money.of("8.00", PEN), 30, Money.of("15.00", PEN), Money.of("50.00", PEN), later);

		assertThat(zone.getName()).isEqualTo("Uptown");
		assertThat(zone.getFee()).isEqualTo(Money.of("8.00", PEN));
		assertThat(zone.getDeliveryMinutes()).isEqualTo(30);
		assertThat(zone.getUpdatedAt()).isEqualTo(later);
	}

}
