package com.jhanantezana.jugueria.inventory.web;

import java.util.UUID;

import com.jhanantezana.jugueria.inventory.internal.StockItemView;

/** {@code available} is what can still be sold; {@code low} is true when it is at or below the threshold. */
record StockItemResponse(UUID productId, boolean tracked, int onHand, int reserved, int available,
		int lowStockThreshold, boolean low) {

	static StockItemResponse from(StockItemView item) {
		return new StockItemResponse(item.productId(), item.tracked(), item.onHand(), item.reserved(),
				item.available(), item.lowStockThreshold(), item.low());
	}

}
