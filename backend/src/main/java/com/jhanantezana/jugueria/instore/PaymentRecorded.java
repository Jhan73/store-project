package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/**
 * {@code received} and {@code change} are set for cash only; {@code lineIds} for a payment made by items.
 * {@code ticketClosed} is true when this payment covered the total. The wallet provider is set for a wallet payment
 * only, with its name as it was when the payment was taken.
 */
public record PaymentRecorded(UUID paymentId, UUID ticketId, UUID shiftId, PaymentMethod method,
		@Nullable UUID walletProviderId, @Nullable String walletProviderName, Money paid, @Nullable Money received,
		@Nullable Money change, List<UUID> lineIds, boolean ticketClosed, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
