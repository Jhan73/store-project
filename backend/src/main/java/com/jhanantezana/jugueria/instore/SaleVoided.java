package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/** {@code ticketType} and {@code quickSale} are those of the ticket whose sale was voided. */
public record SaleVoided(UUID ticketId, UUID saleVoidId, TicketType ticketType, boolean quickSale, Money total,
		List<SoldLine> lines, String reasonCode, @Nullable String note, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
