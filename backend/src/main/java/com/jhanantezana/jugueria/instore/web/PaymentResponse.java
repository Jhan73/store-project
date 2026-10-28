package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.internal.PaymentView;
import com.jhanantezana.jugueria.shared.Money;

/** {@code received} and {@code change} are for cash; a voided payment stays listed with its reason. */
record PaymentResponse(UUID id, PaymentMethod method, @Nullable UUID walletProviderId,
		@Nullable String walletProviderName, Money paid, @Nullable Money received, @Nullable Money change,
		List<UUID> lineIds, UUID recordedBy, Instant recordedAt, boolean voided, @Nullable String voidReasonCode,
		@Nullable String voidNote) {

	static PaymentResponse from(PaymentView payment) {
		return new PaymentResponse(payment.id(), payment.method(), payment.walletProviderId(),
				payment.walletProviderName(), payment.paid(), payment.received(), payment.change(),
				payment.lineIds(), payment.recordedBy(), payment.recordedAt(), payment.voided(), payment.voidReasonCode(),
				payment.voidNote());
	}

}
