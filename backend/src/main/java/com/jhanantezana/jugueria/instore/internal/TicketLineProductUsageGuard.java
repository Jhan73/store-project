package com.jhanantezana.jugueria.instore.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductUsageGuard;

/** A product on any ticket line, sent or not, is part of a ticket's history. */
@Component
class TicketLineProductUsageGuard implements ProductUsageGuard {

	private final JdbcClient jdbc;

	TicketLineProductUsageGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<UUID> productsInUse(Collection<UUID> productIds) {
		return new HashSet<>(jdbc.sql("SELECT DISTINCT product_id FROM instore.ticket_line WHERE product_id IN (:ids)")
			.param("ids", productIds)
			.query(UUID.class)
			.list());
	}

}
