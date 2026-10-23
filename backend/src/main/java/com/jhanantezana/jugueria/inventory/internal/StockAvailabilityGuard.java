package com.jhanantezana.jugueria.inventory.internal;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductAvailabilityGuard;
import com.jhanantezana.jugueria.inventory.InventoryError;
import com.jhanantezana.jugueria.shared.BusinessException;

/** A stock-controlled product with nothing left to sell cannot be put back on the menu by hand. */
@Component
class StockAvailabilityGuard implements ProductAvailabilityGuard {

	private final JdbcClient jdbc;

	StockAvailabilityGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void requireCanBeMarkedAvailable(UUID productId) {
		var available = jdbc.sql("SELECT on_hand - reserved FROM inventory.stock_item WHERE product_id = :product AND tracked")
			.param("product", productId)
			.query(Integer.class)
			.optional();
		if (available.isPresent() && available.get() <= 0) {
			throw new BusinessException(InventoryError.PRODUCT_OUT_OF_STOCK,
					"Product " + productId + " has no stock left to sell",
					Map.of("productId", productId.toString(), "available", available.get()));
		}
	}

}
