package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Money;

/**
 * {@code paid} counts the payments that were not voided and {@code balance} is what is still owed, zero
 * once the ticket is closed.
 */
public record TicketView(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
		@Nullable String counterName, @Nullable String note, TicketStatus status, boolean billRequested, Money total,
		Instant openedAt, List<TicketLineView> lines, boolean quickSale, Money paid, Money balance,
		List<PaymentView> payments, @Nullable SaleVoidView saleVoid, @Nullable UUID mergedIntoId) {

	// A response stored before payments existed has none of these fields.
	public TicketView {
		payments = payments == null ? List.of() : List.copyOf(payments);
		paid = paid == null ? Money.zero(total.currency()) : paid;
		balance = balance == null ? total.minus(paid) : balance;
	}

}
