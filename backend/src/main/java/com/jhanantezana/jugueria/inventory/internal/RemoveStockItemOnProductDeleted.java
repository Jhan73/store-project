package com.jhanantezana.jugueria.inventory.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductChanged;

/**
 * A product that is deleted had no stock history (that is what let it be deleted), so what is left is the stock
 * item that tracking created. Idempotent: a second delivery finds nothing to remove.
 */
@Component
class RemoveStockItemOnProductDeleted {

	private final JdbcClient jdbc;

	RemoveStockItemOnProductDeleted(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@ApplicationModuleListener
	void on(ProductChanged event) {
		if (event.after() != null) {
			return;
		}
		jdbc.sql("""
				DELETE FROM inventory.stock_item i WHERE i.product_id = :product
					AND NOT EXISTS (SELECT 1 FROM inventory.stock_movement m WHERE m.product_id = i.product_id)
					AND NOT EXISTS (SELECT 1 FROM inventory.stock_reservation r WHERE r.product_id = i.product_id)
				""").param("product", event.productId()).update();
	}

}
