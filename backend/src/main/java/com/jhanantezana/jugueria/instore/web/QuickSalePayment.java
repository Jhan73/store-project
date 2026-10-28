package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotNull;

/**
 * Cash states {@code receivedAmount}, what the customer handed over; no other method does. A wallet payment names
 * its {@code walletProviderId}.
 */
record QuickSalePayment(@NotNull PaymentMethod method, @Nullable Money receivedAmount,
		@Nullable UUID walletProviderId) {
}
