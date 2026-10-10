package com.jhanantezana.jugueria.inventory;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** An admin changed a product's stock settings or recorded an entry or adjustment; sales are in the ledger only. */
public record StockAdjusted(UUID productId, StockChangeKind kind, StockSnapshot before, StockSnapshot after,
		@Nullable String reasonCode, @Nullable String note, @Nullable UUID actorId, @Nullable Role actorRole,
		Instant occurredAt) {
}
