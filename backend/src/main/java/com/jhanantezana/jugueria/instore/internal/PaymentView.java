package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.shared.Money;

/** {@code received} and {@code change} are for cash; {@code lineIds} are the lines of a payment made by items. */
public record PaymentView(UUID id, PaymentMethod method, Money paid, @Nullable Money received, @Nullable Money change,
		List<UUID> lineIds, UUID recordedBy, Instant recordedAt, boolean voided, @Nullable String voidReasonCode,
		@Nullable String voidNote) {

	public PaymentView {
		lineIds = lineIds == null ? List.of() : List.copyOf(lineIds);
	}

}
