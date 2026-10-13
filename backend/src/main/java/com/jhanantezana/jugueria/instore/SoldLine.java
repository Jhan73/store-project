package com.jhanantezana.jugueria.instore;

import java.util.List;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

/** A line of a recorded sale as it was charged: names and prices are the snapshot, not the current catalog. */
public record SoldLine(UUID lineId, UUID productId, String productName, int quantity, Money unitPrice,
		Money lineTotal, List<String> modifiers) {

	public SoldLine {
		modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
	}

}
