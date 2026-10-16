package com.jhanantezana.jugueria.notifications;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

/**
 * What the shift summary email reports. {@code countedCash} and {@code difference} are null for a forced close, which
 * carries the reason in {@code note}; {@code method} is the payment method's code.
 */
public record ShiftSummaryEmail(String cashier, Instant closedAt, ZoneId zone, boolean forced, Money openingFloat,
		@Nullable Money countedCash, Money expectedCash, @Nullable Money difference, @Nullable String note,
		Money cashReceived, Money changeGiven, Money voidedCash, Money cashIn, Money cashOut,
		List<MethodTotal> byMethod) {

	public ShiftSummaryEmail {
		byMethod = List.copyOf(byMethod);
	}

	public record MethodTotal(String method, Money recorded, Money voided, Money net) {
	}

}
