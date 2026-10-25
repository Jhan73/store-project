package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/**
 * {@code waste} is true for a line voided after it was ready. {@code amount} is what the line charged, null in events
 * stored before it existed; {@code ticketClosed} is true when this void left the ticket exactly paid and closed it;
 * {@code productName} is the name on the line, null in events stored before it existed.
 */
public record TicketLineVoided(UUID ticketId, UUID lineId, UUID productId, int quantity, boolean waste,
		String reasonCode, @Nullable String note, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt,
		@Nullable Money amount, boolean ticketClosed, @Nullable String productName) {
}
