package com.jhanantezana.jugueria.reporting.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.instore.SoldLine;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.store.StoreApi;

/** Writes the sales lines of one sale or sale void; called from the listener's transaction. */
@Component
class SalesLineRecorder {

	private final SalesLines salesLines;

	private final StoreApi store;

	private final CatalogApi catalog;

	SalesLineRecorder(SalesLines salesLines, StoreApi store, CatalogApi catalog) {
		this.salesLines = salesLines;
		this.store = store;
		this.catalog = catalog;
	}

	void record(UUID sourceId, UUID ticketId, TicketType ticketType, boolean quickSale, List<SoldLine> lines,
			boolean voided, @Nullable UUID employeeId, Instant occurredAt) {
		var fact = new SalesLines.Fact(sourceId, ticketId, occurredAt, BusinessMoment.of(occurredAt, store.timeZone()),
				ticketType, quickSale, voided, voided ? -1 : 1, employeeId);
		var categories = catalog.categoriesOf(lines.stream().map(SoldLine::productId).distinct().toList());
		for (var line : lines) {
			salesLines.insert(fact, line, categories.get(line.productId()));
		}
	}

}
