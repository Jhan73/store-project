package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.shared.Money;

/** The SQL of the cash in and out of a shift's drawer. */
@Component
class CashMovementLog {

	private final JdbcClient jdbc;

	CashMovementLog(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(UUID id, UUID shiftId, CashMovementType type, Money amount, @Nullable String reasonCode,
			@Nullable String note, @Nullable String reference, UUID recordedBy, Instant now) {
		jdbc.sql("""
				INSERT INTO instore.cash_movement (id, shift_id, type, currency, amount, reason_code, note, reference,
					recorded_by, recorded_at)
				VALUES (:id, :shift, :type, :currency, :amount, :reason, :note, :reference, :by, :now)
				""")
			.param("id", id)
			.param("shift", shiftId)
			.param("type", type.name())
			.param("currency", amount.currency().getCurrencyCode())
			.param("amount", amount.amount())
			.param("reason", reasonCode)
			.param("note", note)
			.param("reference", reference)
			.param("by", recordedBy)
			.param("now", Tickets.utc(now))
			.update();
	}

	CashMovementView require(UUID id) {
		return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query((rs, row) -> view(rs)).single();
	}

	List<CashMovementView> ofShift(UUID shiftId) {
		return jdbc.sql(SELECT + " WHERE shift_id = :shift ORDER BY recorded_at, id")
			.param("shift", shiftId)
			.query((rs, row) -> view(rs))
			.list();
	}

	private static final String SELECT = """
			SELECT id, shift_id, type, currency, amount, reason_code, note, reference, recorded_by, recorded_at
			FROM instore.cash_movement
			""";

	private static CashMovementView view(java.sql.ResultSet rs) throws java.sql.SQLException {
		var currency = Currency.getInstance(rs.getString("currency").trim());
		return new CashMovementView(rs.getObject("id", UUID.class), rs.getObject("shift_id", UUID.class),
				CashMovementType.valueOf(rs.getString("type")), new Money(rs.getBigDecimal("amount"), currency),
				rs.getString("reason_code"), rs.getString("note"), rs.getString("reference"),
				rs.getObject("recorded_by", UUID.class), rs.getObject("recorded_at", OffsetDateTime.class).toInstant());
	}

}
