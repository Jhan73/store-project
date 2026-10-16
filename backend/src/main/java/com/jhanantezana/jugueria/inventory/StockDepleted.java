package com.jhanantezana.jugueria.inventory;

import java.time.Instant;
import java.util.UUID;

/** Nothing is left to sell: the product was just made unavailable. */
public record StockDepleted(UUID productId, Instant occurredAt) {
}
