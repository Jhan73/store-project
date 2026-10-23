package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/**
 * A sent line was served free of charge and counts as waste. {@code amount} is what the line would have charged;
 * {@code ticketClosed} is true when this comp left the ticket exactly paid and closed it. The last five components are
 * the line's snapshot, so consumers need not read the ticket.
 */
public record TicketLineComped(UUID ticketId, UUID lineId, UUID productId, int quantity, String reasonCode,
		@Nullable String note, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt, Money amount,
		boolean ticketClosed, TicketType ticketType, boolean quickSale, String productName, Money unitPrice,
		List<String> modifiers) {

	public TicketLineComped {
		modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
	}

}
