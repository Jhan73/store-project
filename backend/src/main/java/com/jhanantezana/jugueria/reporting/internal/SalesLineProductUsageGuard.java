package com.jhanantezana.jugueria.reporting.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductUsageGuard;

/** A product in any recorded sale line is part of the sales history the reports read. */
@Component
class SalesLineProductUsageGuard implements ProductUsageGuard {

	private final JdbcClient jdbc;

	SalesLineProductUsageGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<UUID> productsInUse(Collection<UUID> productIds) {
		return new HashSet<>(jdbc.sql("SELECT DISTINCT product_id FROM reporting.sales_line WHERE product_id IN (:ids)")
			.param("ids", productIds)
			.query(UUID.class)
			.list());
	}

}
