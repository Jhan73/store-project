package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotNull;

/** Cash states {@code receivedAmount}, what the customer handed over; no other method does. */
record QuickSalePayment(@NotNull PaymentMethod method, @Nullable Money receivedAmount) {
}
