package com.jhanantezana.jugueria.inventory;

import java.time.Instant;
import java.util.UUID;

/** What can still be sold just fell to the product's low-stock threshold or below. */
public record StockLow(UUID productId, int available, int threshold, Instant occurredAt) {
}
