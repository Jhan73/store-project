package com.jhanantezana.jugueria.inventory.internal;

import java.util.UUID;

/** {@code available} is what can still be sold: on hand minus reserved. */
public record StockItemView(UUID productId, boolean tracked, int onHand, int reserved, int available,
		int lowStockThreshold, boolean low) {

	static StockItemView of(UUID productId, boolean tracked, int onHand, int reserved, int lowStockThreshold) {
		var available = onHand - reserved;
		return new StockItemView(productId, tracked, onHand, reserved, available, lowStockThreshold,
				tracked && available <= lowStockThreshold);
	}

}
