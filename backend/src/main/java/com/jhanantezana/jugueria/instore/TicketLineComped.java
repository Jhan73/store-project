package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/**
 * A sent line was served free of charge and counts as waste. {@code amount} is what the line would have charged;
 * {@code ticketClosed} is true when this comp left the ticket exactly paid and closed it.
 */
public record TicketLineComped(UUID ticketId, UUID lineId, UUID productId, int quantity, String reasonCode,
		@Nullable String note, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt, Money amount,
		boolean ticketClosed) {
}
