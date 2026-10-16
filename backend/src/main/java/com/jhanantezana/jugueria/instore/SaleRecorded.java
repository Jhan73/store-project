package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/** A ticket was paid in full and closed; {@code lines} are the ones charged, with voided and comped ones left out. */
public record SaleRecorded(UUID ticketId, long number, TicketType type, boolean quickSale, Money total,
		List<SoldLine> lines, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {
}
