package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/**
 * {@code shiftId} is the shift whose drawer paid the money back, null when the voider had no open shift.
 * {@code saleVoidId} is set when the void is part of voiding the whole sale.
 */
public record PaymentVoided(UUID paymentId, UUID ticketId, @Nullable UUID shiftId, PaymentMethod method,
		@Nullable String walletProviderName, Money amount, String reasonCode, @Nullable String note,
		@Nullable UUID saleVoidId, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
