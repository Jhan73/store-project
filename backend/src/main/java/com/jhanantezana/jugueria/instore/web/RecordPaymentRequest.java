package com.jhanantezana.jugueria.instore.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.internal.TicketLimits;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code amount} is what the payment settles on the bill. Cash states {@code receivedAmount}, what the customer
 * handed over, and the change follows; no other method does. {@code lineIds} pays those lines by items and
 * {@code amount} must then equal their total. A wallet payment names the {@code walletProviderId} it was made with;
 * no other method does.
 */
record RecordPaymentRequest(@NotNull PaymentMethod method, @NotNull Money amount, @Nullable Money receivedAmount,
		@Nullable UUID walletProviderId,
		@Nullable @Size(min = 1, max = TicketLimits.MAX_REPEATED_LINES) List<@NotNull UUID> lineIds) {
}
