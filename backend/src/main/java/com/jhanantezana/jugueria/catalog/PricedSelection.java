package com.jhanantezana.jugueria.catalog;

import java.util.List;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

/** What a customer or a server chose, priced from the database at the moment of asking (BR-03 snapshot source). */
public record PricedSelection(UUID productId, String productName, Money unitPrice, List<PricedOption> options) {
}
