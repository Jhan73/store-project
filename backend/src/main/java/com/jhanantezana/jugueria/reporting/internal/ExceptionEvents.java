package com.jhanantezana.jugueria.reporting.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.StoreApi;

@Component
class ExceptionEvents {

	private final JdbcClient jdbc;

	private final StoreApi store;

	ExceptionEvents(JdbcClient jdbc, StoreApi store) {
		this.jdbc = jdbc;
		this.store = store;
	}

	record Entry(ExceptionType type, UUID sourceId, Instant occurredAt, @Nullable UUID employeeId,
			@Nullable String reasonCode, @Nullable String note, @Nullable Money amount, @Nullable UUID ticketId,
			@Nullable UUID shiftId) {
	}

	/** Unique on the type and the source: a redelivered event finds its row already written. */
	void record(Entry entry) {
		var amount = entry.amount();
		jdbc.sql("""
				INSERT INTO reporting.exception_event (id, type, source_id, occurred_at, business_date, employee_id,
					reason_code, note, amount_amount, currency, ticket_id, shift_id)
				VALUES (:id, :type, :source, :occurredAt, :date, :employee, :reason, :note, :amount, :currency, :ticket,
					:shift)
				ON CONFLICT (type, source_id) DO NOTHING
				""")
			.param("id", Ids.newId())
			.param("type", entry.type().name())
			.param("source", entry.sourceId())
			.param("occurredAt", OffsetDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC))
			.param("date", BusinessMoment.of(entry.occurredAt(), store.timeZone()).date())
			.param("employee", entry.employeeId())
			.param("reason", entry.reasonCode())
			.param("note", entry.note())
			.param("amount", amount == null ? null : amount.amount())
			.param("currency", amount == null ? null : amount.currency().getCurrencyCode())
			.param("ticket", entry.ticketId())
			.param("shift", entry.shiftId())
			.update();
	}

}
