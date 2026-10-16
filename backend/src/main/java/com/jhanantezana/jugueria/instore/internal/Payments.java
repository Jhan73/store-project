package com.jhanantezana.jugueria.instore.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.shared.Money;

/** The SQL of in-store payments and their voids; the rules that decide with it live in the services. */
@Component
class Payments {

	record PaymentRow(UUID id, UUID ticketId, UUID shiftId, PaymentMethod method, Currency currency, BigDecimal paid,
			@Nullable BigDecimal received, BigDecimal change, boolean voided) {

		Money paidMoney() {
			return new Money(paid, currency);
		}

	}

	private static final String SELECT = """
			SELECT p.id, p.ticket_id, p.shift_id, p.method, p.currency, p.paid_amount, p.received_amount, p.change_amount,
				p.recorded_by, p.recorded_at, v.id IS NOT NULL AS voided, v.reason_code AS void_reason, v.note AS void_note
			FROM instore.in_store_payment p LEFT JOIN instore.payment_void v ON v.payment_id = p.id
			""";

	private final JdbcClient jdbc;

	Payments(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(UUID id, UUID ticketId, UUID shiftId, PaymentMethod method, Money paid, @Nullable Money received,
			Money change, List<UUID> lineIds, UUID recordedBy, Instant now) {
		jdbc.sql("""
				INSERT INTO instore.in_store_payment (id, ticket_id, shift_id, method, currency, paid_amount, received_amount,
					change_amount, recorded_by, recorded_at)
				VALUES (:id, :ticket, :shift, :method, :currency, :paid, :received, :change, :by, :now)
				""")
			.param("id", id)
			.param("ticket", ticketId)
			.param("shift", shiftId)
			.param("method", method.name())
			.param("currency", paid.currency().getCurrencyCode())
			.param("paid", paid.amount())
			.param("received", received == null ? null : received.amount())
			.param("change", change.amount())
			.param("by", recordedBy)
			.param("now", Tickets.utc(now))
			.update();
		for (var lineId : lineIds) {
			jdbc.sql("INSERT INTO instore.payment_line (payment_id, line_id) VALUES (:payment, :line)")
				.param("payment", id)
				.param("line", lineId)
				.update();
		}
	}

	/** What the live (not voided) payments of the ticket add up to. */
	BigDecimal livePaid(UUID ticketId) {
		return jdbc.sql("""
				SELECT coalesce(sum(p.paid_amount), 0) FROM instore.in_store_payment p
				WHERE p.ticket_id = :ticket AND NOT EXISTS (SELECT 1 FROM instore.payment_void v WHERE v.payment_id = p.id)
				""").param("ticket", ticketId).query(BigDecimal.class).single();
	}

	/** The lines a live payment already settles. */
	Set<UUID> coveredLines(UUID ticketId) {
		return new HashSet<>(jdbc.sql("""
				SELECT pl.line_id FROM instore.payment_line pl JOIN instore.in_store_payment p ON p.id = pl.payment_id
				WHERE p.ticket_id = :ticket AND NOT EXISTS (SELECT 1 FROM instore.payment_void v WHERE v.payment_id = p.id)
				""").param("ticket", ticketId).query(UUID.class).list());
	}

	Optional<PaymentRow> find(UUID ticketId, UUID paymentId) {
		return jdbc.sql(SELECT + " WHERE p.ticket_id = :ticket AND p.id = :id")
			.param("ticket", ticketId)
			.param("id", paymentId)
			.query((rs, row) -> row(rs))
			.optional();
	}

	List<PaymentRow> live(UUID ticketId) {
		return jdbc.sql(SELECT + " WHERE p.ticket_id = :ticket AND v.id IS NULL ORDER BY p.recorded_at, p.id")
			.param("ticket", ticketId)
			.query((rs, row) -> row(rs))
			.list();
	}

	/** Every payment of the ticket, voided ones included, oldest first. */
	List<PaymentView> ofTicket(UUID ticketId) {
		var lines = new HashMap<UUID, List<UUID>>();
		jdbc.sql("""
				SELECT pl.payment_id, pl.line_id FROM instore.payment_line pl
				JOIN instore.in_store_payment p ON p.id = pl.payment_id WHERE p.ticket_id = :ticket ORDER BY pl.line_id
				""")
			.param("ticket", ticketId)
			.query((rs, row) -> Map.entry(rs.getObject("payment_id", UUID.class), rs.getObject("line_id", UUID.class)))
			.list()
			.forEach(entry -> lines.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue()));
		return jdbc.sql(SELECT + " WHERE p.ticket_id = :ticket ORDER BY p.recorded_at, p.id")
			.param("ticket", ticketId)
			.query((rs, row) -> view(rs, lines))
			.list();
	}

	/** @return false when the payment was voided already */
	boolean insertVoid(UUID id, UUID paymentId, UUID ticketId, @Nullable UUID shiftId, @Nullable UUID saleVoidId,
			String reasonCode, @Nullable String note, UUID voidedBy, Instant now) {
		return jdbc.sql("""
				INSERT INTO instore.payment_void (id, payment_id, ticket_id, shift_id, sale_void_id, reason_code, note,
					voided_by, voided_at)
				VALUES (:id, :payment, :ticket, :shift, :saleVoid, :reason, :note, :by, :now)
				ON CONFLICT (payment_id) DO NOTHING
				""")
			.param("id", id)
			.param("payment", paymentId)
			.param("ticket", ticketId)
			.param("shift", shiftId)
			.param("saleVoid", saleVoidId)
			.param("reason", reasonCode)
			.param("note", note)
			.param("by", voidedBy)
			.param("now", Tickets.utc(now))
			.update() == 1;
	}

	/** @return false when the sale was voided already */
	boolean insertSaleVoid(UUID id, UUID ticketId, String reasonCode, @Nullable String note, UUID voidedBy, Instant now) {
		return jdbc.sql("""
				INSERT INTO instore.sale_void (id, ticket_id, reason_code, note, voided_by, voided_at)
				VALUES (:id, :ticket, :reason, :note, :by, :now) ON CONFLICT (ticket_id) DO NOTHING
				""")
			.param("id", id)
			.param("ticket", ticketId)
			.param("reason", reasonCode)
			.param("note", note)
			.param("by", voidedBy)
			.param("now", Tickets.utc(now))
			.update() == 1;
	}

	Optional<SaleVoidView> saleVoidOf(UUID ticketId) {
		return jdbc.sql("SELECT reason_code, note, voided_by, voided_at FROM instore.sale_void WHERE ticket_id = :ticket")
			.param("ticket", ticketId)
			.query((rs, row) -> new SaleVoidView(rs.getString("reason_code"), rs.getString("note"),
					rs.getObject("voided_by", UUID.class), rs.getObject("voided_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	private static PaymentRow row(ResultSet rs) throws SQLException {
		return new PaymentRow(rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class),
				rs.getObject("shift_id", UUID.class), PaymentMethod.valueOf(rs.getString("method")),
				Currency.getInstance(rs.getString("currency").trim()), rs.getBigDecimal("paid_amount"),
				rs.getBigDecimal("received_amount"), rs.getBigDecimal("change_amount"), rs.getBoolean("voided"));
	}

	private static PaymentView view(ResultSet rs, Map<UUID, List<UUID>> lines) throws SQLException {
		var currency = Currency.getInstance(rs.getString("currency").trim());
		var id = rs.getObject("id", UUID.class);
		var method = PaymentMethod.valueOf(rs.getString("method"));
		var received = rs.getBigDecimal("received_amount");
		return new PaymentView(id, method, new Money(rs.getBigDecimal("paid_amount"), currency),
				received == null ? null : new Money(received, currency),
				method == PaymentMethod.CASH ? new Money(rs.getBigDecimal("change_amount"), currency) : null,
				lines.getOrDefault(id, List.of()), rs.getObject("recorded_by", UUID.class),
				rs.getObject("recorded_at", OffsetDateTime.class).toInstant(), rs.getBoolean("voided"),
				rs.getString("void_reason"), rs.getString("void_note"));
	}

}
