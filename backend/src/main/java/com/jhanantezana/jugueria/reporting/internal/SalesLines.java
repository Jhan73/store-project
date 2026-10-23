package com.jhanantezana.jugueria.reporting.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductCategory;
import com.jhanantezana.jugueria.instore.SoldLine;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Ids;

import tools.jackson.databind.json.JsonMapper;

@Component
class SalesLines {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	SalesLines(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** What one event says about the ticket it concerns; {@code sign} is -1 for a voided sale, so sums net out. */
	record Fact(UUID sourceId, UUID ticketId, Instant occurredAt, BusinessMoment moment, TicketType ticketType,
			boolean quickSale, boolean voided, int sign, @Nullable UUID employeeId, boolean comped) {
	}

	/** Unique on the source and the line: a redelivered event finds its rows already written. */
	void insert(Fact fact, SoldLine line, @Nullable ProductCategory category) {
		jdbc.sql("""
				INSERT INTO reporting.sales_line (id, source_id, line_id, ticket_id, occurred_at, business_date, hour,
					weekday, channel, ticket_type, quick_sale, product_id, product_name, category_id, category_name,
					modifiers, quantity, unit_price_amount, line_total_amount, currency, voided, comped, waste, employee_id)
				VALUES (:id, :source, :line, :ticket, :occurredAt, :date, :hour, :weekday, 'IN_STORE', :ticketType,
					:quickSale, :product, :productName, :categoryId, :categoryName, CAST(:modifiers AS jsonb), :quantity,
					:unitPrice, :lineTotal, :currency, :voided, :comped, :comped, :employee)
				ON CONFLICT (source_id, line_id) DO NOTHING
				""")
			.param("id", Ids.newId())
			.param("source", fact.sourceId())
			.param("line", line.lineId())
			.param("ticket", fact.ticketId())
			.param("occurredAt", OffsetDateTime.ofInstant(fact.occurredAt(), ZoneOffset.UTC))
			.param("date", fact.moment().date())
			.param("hour", fact.moment().hour())
			.param("weekday", fact.moment().weekday())
			.param("ticketType", fact.ticketType().name())
			.param("quickSale", fact.quickSale())
			.param("product", line.productId())
			.param("productName", line.productName())
			.param("categoryId", category == null ? null : category.categoryId())
			.param("categoryName", category == null ? null : category.categoryName())
			.param("modifiers", json.writeValueAsString(line.modifiers()))
			.param("quantity", fact.sign() * line.quantity())
			.param("unitPrice", line.unitPrice().amount())
			.param("lineTotal", fact.sign() == 1 ? line.lineTotal().amount() : line.lineTotal().amount().negate())
			.param("currency", line.lineTotal().currency().getCurrencyCode())
			.param("voided", fact.voided())
			.param("comped", fact.comped())
			.param("employee", fact.employeeId())
			.update();
	}

}
