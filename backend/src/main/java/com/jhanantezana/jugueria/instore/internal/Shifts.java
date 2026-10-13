package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.shared.Money;

import tools.jackson.databind.json.JsonMapper;

/** The SQL of register shifts; the rules that decide with it live in the services. */
@Component
class Shifts {

	record ShiftRow(UUID id, UUID cashierId, ShiftStatus status, Currency currency, BigDecimal openingFloat,
			Instant openedAt, @Nullable BigDecimal counted, @Nullable BigDecimal expected,
			@Nullable BigDecimal difference, @Nullable String differenceNote, @Nullable ShiftCloseKind closeKind,
			@Nullable String forceReason, @Nullable UUID closedBy, @Nullable Instant closedAt,
			@Nullable ShiftTotals totals) {

		Money floatMoney() {
			return new Money(openingFloat, currency);
		}

	}

	/** A shift row held against closing until the transaction ends. */
	record LockedShift(UUID id, UUID cashierId, ShiftStatus status, boolean countRevealed) {
	}

	private record MethodSums(BigDecimal paid, BigDecimal received, BigDecimal change) {
	}

	private static final String COLUMNS = """
			id, cashier_id, status, currency, float_amount, opened_at, counted_amount, expected_amount, difference_amount,
			difference_note, close_kind, force_reason, closed_by, closed_at, totals
			FROM instore.register_shift
			""";

	private final JdbcClient jdbc;

	private final JsonMapper json;

	Shifts(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** @return false when the cashier already has an open shift */
	boolean insert(UUID id, UUID cashierId, Currency currency, BigDecimal openingFloat, Instant now) {
		return jdbc.sql("""
				INSERT INTO instore.register_shift (id, cashier_id, status, currency, float_amount, opened_at, updated_at)
				VALUES (:id, :cashier, 'OPEN', :currency, :float, :now, :now)
				ON CONFLICT (cashier_id) WHERE status = 'OPEN' DO NOTHING
				""")
			.param("id", id)
			.param("cashier", cashierId)
			.param("currency", currency.getCurrencyCode())
			.param("float", openingFloat)
			.param("now", Tickets.utc(now))
			.update() == 1;
	}

	Optional<ShiftRow> find(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " WHERE id = :id").param("id", id).query((rs, row) -> shift(rs)).optional();
	}

	Optional<ShiftRow> findOpenOf(UUID cashierId) {
		return jdbc.sql("SELECT " + COLUMNS + " WHERE cashier_id = :cashier AND status = 'OPEN'")
			.param("cashier", cashierId)
			.query((rs, row) -> shift(rs))
			.optional();
	}

	/** Shares the cashier's open shift, so it cannot close until this transaction ends; empty without one. */
	Optional<LockedShift> shareOpenOf(UUID cashierId) {
		return jdbc.sql("""
				SELECT id, cashier_id, status, counted_amount IS NOT NULL AS revealed FROM instore.register_shift
				WHERE cashier_id = :cashier AND status = 'OPEN' FOR SHARE
				""").param("cashier", cashierId).query((rs, row) -> locked(rs)).optional();
	}

	Optional<LockedShift> share(UUID id) {
		return jdbc.sql("""
				SELECT id, cashier_id, status, counted_amount IS NOT NULL AS revealed FROM instore.register_shift
				WHERE id = :id FOR SHARE
				""").param("id", id).query((rs, row) -> locked(rs)).optional();
	}

	/** Takes the exclusive lock a close starts with; false when the shift is not open (or not the cashier's). */
	boolean lockOpen(UUID id, @Nullable UUID onlyCashier, Instant now) {
		return jdbc.sql("""
				UPDATE instore.register_shift SET version = version + 1, updated_at = :now
				WHERE id = :id AND status = 'OPEN' AND (CAST(:cashier AS uuid) IS NULL OR cashier_id = :cashier)
				""").param("now", Tickets.utc(now)).param("id", id).param("cashier", onlyCashier).update() == 1;
	}

	void storeRevealedCount(UUID id, BigDecimal counted, Instant now) {
		jdbc.sql("""
				UPDATE instore.register_shift SET counted_amount = :counted, counted_at = :now, updated_at = :now
				WHERE id = :id AND status = 'OPEN' AND counted_amount IS NULL
				""").param("counted", counted).param("now", Tickets.utc(now)).param("id", id).update();
	}

	void close(UUID id, ShiftCloseKind kind, @Nullable BigDecimal counted, BigDecimal expected,
			@Nullable BigDecimal difference, @Nullable String differenceNote, @Nullable String forceReason, UUID closedBy,
			ShiftTotals totals, Instant now) {
		var closed = jdbc.sql("""
				UPDATE instore.register_shift
				SET status = 'CLOSED', close_kind = :kind, counted_amount = :counted, counted_at = coalesce(counted_at, :now),
					expected_amount = :expected, difference_amount = :difference, difference_note = :note,
					force_reason = :reason, closed_by = :closedBy, closed_at = :now, totals = CAST(:totals AS jsonb),
					updated_at = :now
				WHERE id = :id AND status = 'OPEN'
				""")
			.param("kind", kind.name())
			.param("counted", counted)
			.param("expected", expected)
			.param("difference", difference)
			.param("note", differenceNote)
			.param("reason", forceReason)
			.param("closedBy", closedBy)
			.param("totals", json.writeValueAsString(totals))
			.param("now", Tickets.utc(now))
			.param("id", id)
			.update();
		if (closed != 1) {
			throw new IllegalStateException("Shift " + id + " changed state while it was locked");
		}
	}

	/** What moved through the shift so far; every payment method is listed, taken or not. */
	ShiftTotals totals(UUID shiftId, Currency currency) {
		var taken = new EnumMap<PaymentMethod, MethodSums>(PaymentMethod.class);
		jdbc.sql("""
				SELECT method, sum(paid_amount) AS paid, sum(coalesce(received_amount, 0)) AS received,
					sum(change_amount) AS change_total
				FROM instore.in_store_payment WHERE shift_id = :shift GROUP BY method
				""")
			.param("shift", shiftId)
			.query((rs, i) -> Map.entry(PaymentMethod.valueOf(rs.getString("method")),
					new MethodSums(rs.getBigDecimal("paid"), rs.getBigDecimal("received"), rs.getBigDecimal("change_total"))))
			.list()
			.forEach(entry -> taken.put(entry.getKey(), entry.getValue()));
		var returned = new EnumMap<PaymentMethod, BigDecimal>(PaymentMethod.class);
		jdbc.sql("""
				SELECT p.method, sum(p.paid_amount) AS paid FROM instore.payment_void v
				JOIN instore.in_store_payment p ON p.id = v.payment_id WHERE v.shift_id = :shift GROUP BY p.method
				""")
			.param("shift", shiftId)
			.query((rs, i) -> Map.entry(PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("paid")))
			.list()
			.forEach(entry -> returned.put(entry.getKey(), entry.getValue()));
		var moved = new java.util.HashMap<String, BigDecimal>();
		jdbc.sql("SELECT type, sum(amount) AS total FROM instore.cash_movement WHERE shift_id = :shift GROUP BY type")
			.param("shift", shiftId)
			.query((rs, i) -> Map.entry(rs.getString("type"), rs.getBigDecimal("total")))
			.list()
			.forEach(entry -> moved.put(entry.getKey(), entry.getValue()));
		var byMethod = new ArrayList<ShiftTotals.MethodTotal>();
		for (var method : PaymentMethod.values()) {
			var recorded = money(taken.containsKey(method) ? taken.get(method).paid() : BigDecimal.ZERO, currency);
			var voided = money(returned.get(method), currency);
			byMethod.add(new ShiftTotals.MethodTotal(method, recorded, voided, recorded.minus(voided)));
		}
		var cash = taken.get(PaymentMethod.CASH);
		return new ShiftTotals(money(cash == null ? null : cash.received(), currency),
				money(cash == null ? null : cash.change(), currency), money(returned.get(PaymentMethod.CASH), currency),
				money(moved.get("IN"), currency), money(moved.get("OUT"), currency), byMethod);
	}

	long count(@Nullable ShiftStatus status) {
		return jdbc.sql("SELECT count(*) FROM instore.register_shift WHERE (CAST(:status AS varchar) IS NULL OR status = :status)")
			.param("status", status == null ? null : status.name())
			.query(Long.class)
			.single();
	}

	List<ShiftRow> page(@Nullable ShiftStatus status, int limit, long offset) {
		return jdbc.sql("SELECT " + COLUMNS + """
				 WHERE (CAST(:status AS varchar) IS NULL OR status = :status)
				ORDER BY opened_at DESC, id DESC LIMIT :limit OFFSET :offset
				""")
			.param("status", status == null ? null : status.name())
			.param("limit", limit)
			.param("offset", offset)
			.query((rs, row) -> shift(rs))
			.list();
	}

	private static Money money(@Nullable BigDecimal amount, Currency currency) {
		return new Money(amount == null ? BigDecimal.ZERO : amount, currency);
	}

	private LockedShift locked(ResultSet rs) throws SQLException {
		return new LockedShift(rs.getObject("id", UUID.class), rs.getObject("cashier_id", UUID.class),
				ShiftStatus.valueOf(rs.getString("status")), rs.getBoolean("revealed"));
	}

	private ShiftRow shift(ResultSet rs) throws SQLException {
		var closedAt = rs.getObject("closed_at", OffsetDateTime.class);
		var closeKind = rs.getString("close_kind");
		var totals = rs.getString("totals");
		return new ShiftRow(rs.getObject("id", UUID.class), rs.getObject("cashier_id", UUID.class),
				ShiftStatus.valueOf(rs.getString("status")), Currency.getInstance(rs.getString("currency").trim()),
				rs.getBigDecimal("float_amount"), rs.getObject("opened_at", OffsetDateTime.class).toInstant(),
				rs.getBigDecimal("counted_amount"), rs.getBigDecimal("expected_amount"), rs.getBigDecimal("difference_amount"),
				rs.getString("difference_note"), closeKind == null ? null : ShiftCloseKind.valueOf(closeKind),
				rs.getString("force_reason"), rs.getObject("closed_by", UUID.class),
				closedAt == null ? null : closedAt.toInstant(), totals == null ? null : json.readValue(totals, ShiftTotals.class));
	}

}
