package com.jhanantezana.jugueria.inventory.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductUsageGuard;

/** A product with any stock movement or reservation has history that must keep resolving to it. */
@Component
class StockHistoryProductUsageGuard implements ProductUsageGuard {

	private final JdbcClient jdbc;

	StockHistoryProductUsageGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<UUID> productsInUse(Collection<UUID> productIds) {
		return new HashSet<>(jdbc.sql("""
				SELECT product_id FROM inventory.stock_movement WHERE product_id IN (:ids)
				UNION
				SELECT product_id FROM inventory.stock_reservation WHERE product_id IN (:ids)
				""").param("ids", productIds).query(UUID.class).list());
	}

}
