package com.jhanantezana.jugueria.inventory;

import java.util.UUID;

public record StockLine(UUID productId, int quantity) {

	public StockLine {
		if (quantity <= 0) {
			throw new IllegalArgumentException("A stock line needs a positive quantity");
		}
	}

}
