package com.jhanantezana.jugueria.instore.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The SQL of tickets and their lines; the rules that decide with it live in the services. */
@Component
class Tickets {

	record TicketRow(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
			@Nullable String counterName, @Nullable String note, TicketStatus status, boolean billRequested,
			@Nullable UUID boardSourceId, int boardRounds, Currency currency, Instant createdAt) {
	}

	private static final String TICKET_COLUMNS = """
			k.id, k.number, k.type, k.table_id, t.name AS table_name, k.counter_name, k.note, k.status, k.bill_requested,
			k.board_source_id, k.board_rounds, k.currency, k.created_at
			FROM instore.ticket k LEFT JOIN instore.dining_table t ON t.id = k.table_id
			""";

	private static final String LINE_COLUMNS = """
			id, product_id, product_name, quantity, unit_price_amount, unit_price_currency, modifiers, note, allergy, state,
			sent_at, waste, void_reason_code, void_note
			""";

	private static final TypeReference<List<ChosenOption>> OPTIONS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	private final JsonMapper json;

	Tickets(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	Optional<TicketRow> find(UUID id) {
		return jdbc.sql("SELECT " + TICKET_COLUMNS + " WHERE k.id = :id")
			.param("id", id)
			.query((rs, row) -> ticket(rs))
			.optional();
	}

	TicketRow require(UUID id) {
		return find(id).orElseThrow(() -> new BusinessException(InstoreError.TICKET_NOT_FOUND, "Ticket not found"));
	}

	/**
	 * Takes the ticket's row, the lock every command on a ticket starts with: later statements see everything
	 * committed before it. A ticket that is not open is rejected.
	 */
	TicketRow lockOpen(UUID id, Instant now) {
		var locked = jdbc.sql("""
				UPDATE instore.ticket SET version = version + 1, updated_at = :now WHERE id = :id AND status = 'OPEN'
				""").param("now", utc(now)).param("id", id).update();
		if (locked == 0) {
			var exists = find(id).isPresent();
			throw exists ? new BusinessException(InstoreError.TICKET_CLOSED, "Ticket " + id + " is not open")
					: new BusinessException(InstoreError.TICKET_NOT_FOUND, "Ticket not found");
		}
		return require(id);
	}

	/** @return false when the table already has an open ticket */
	boolean insert(UUID id, TicketType type, @Nullable UUID tableId, @Nullable String counterName, @Nullable String note,
			Currency currency, UUID openedBy, Instant now) {
		return jdbc.sql("""
				INSERT INTO instore.ticket (id, type, table_id, counter_name, note, status, currency, opened_by, created_at,
					updated_at)
				VALUES (:id, :type, :table, :counter, :note, 'OPEN', :currency, :openedBy, :now, :now)
				ON CONFLICT (table_id) WHERE status = 'OPEN' DO NOTHING
				""")
			.param("id", id)
			.param("type", type.name())
			.param("table", tableId)
			.param("counter", counterName)
			.param("note", note)
			.param("currency", currency.getCurrencyCode())
			.param("openedBy", openedBy)
			.param("now", utc(now))
			.update() == 1;
	}

	/** Reads the table and keeps it from being deactivated until this transaction ends. */
	Optional<Boolean> activeTableShared(UUID tableId) {
		return jdbc.sql("SELECT active FROM instore.dining_table WHERE id = :id FOR SHARE")
			.param("id", tableId)
			.query(Boolean.class)
			.optional();
	}

	/** Takes the table's row so no ticket can open on it, or move onto it, until this transaction ends. */
	void lockTable(UUID tableId) {
		jdbc.sql("SELECT 1 FROM instore.dining_table WHERE id = :id FOR UPDATE").param("id", tableId).query().listOfRows();
	}

	boolean hasOpenTicketOn(UUID tableId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM instore.ticket WHERE table_id = :id AND status = 'OPEN')")
			.param("id", tableId)
			.query(Boolean.class)
			.single();
	}

	List<TicketSummary> open() {
		return jdbc.sql("""
				SELECT k.id, k.number, k.type, k.table_id, t.name AS table_name, k.counter_name, k.bill_requested,
					k.currency, k.created_at,
					coalesce((SELECT sum(l.unit_price_amount * l.quantity) FROM instore.ticket_line l
						WHERE l.ticket_id = k.id AND l.state IN ('UNSENT', 'SENT')), 0) AS total
				FROM instore.ticket k LEFT JOIN instore.dining_table t ON t.id = k.table_id
				WHERE k.status = 'OPEN' ORDER BY k.created_at, k.id
				""").query((rs, row) -> summary(rs)).list();
	}

	List<TicketLineView> lines(UUID ticketId) {
		return jdbc.sql("SELECT " + LINE_COLUMNS + " FROM instore.ticket_line WHERE ticket_id = :id ORDER BY position")
			.param("id", ticketId)
			.query((rs, row) -> line(rs))
			.list();
	}

	private TicketRow ticket(ResultSet rs) throws SQLException {
		return new TicketRow(rs.getObject("id", UUID.class), rs.getLong("number"),
				TicketType.valueOf(rs.getString("type")), rs.getObject("table_id", UUID.class), rs.getString("table_name"),
				rs.getString("counter_name"), rs.getString("note"), TicketStatus.valueOf(rs.getString("status")),
				rs.getBoolean("bill_requested"), rs.getObject("board_source_id", UUID.class), rs.getInt("board_rounds"),
				Currency.getInstance(rs.getString("currency").trim()),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	private TicketSummary summary(ResultSet rs) throws SQLException {
		var currency = Currency.getInstance(rs.getString("currency").trim());
		return new TicketSummary(rs.getObject("id", UUID.class), rs.getLong("number"),
				TicketType.valueOf(rs.getString("type")), rs.getObject("table_id", UUID.class), rs.getString("table_name"),
				rs.getString("counter_name"), rs.getBoolean("bill_requested"),
				new Money(rs.getBigDecimal("total"), currency), rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	private TicketLineView line(ResultSet rs) throws SQLException {
		var currency = Currency.getInstance(rs.getString("unit_price_currency").trim());
		var sentAt = rs.getObject("sent_at", OffsetDateTime.class);
		return new TicketLineView(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
				rs.getString("product_name"), rs.getInt("quantity"), new Money(rs.getBigDecimal("unit_price_amount"), currency),
				json.readValue(rs.getString("modifiers"), OPTIONS), rs.getString("note"), rs.getBoolean("allergy"),
				TicketLineState.valueOf(rs.getString("state")), sentAt == null ? null : sentAt.toInstant(),
				rs.getBoolean("waste"), rs.getString("void_reason_code"), rs.getString("void_note"));
	}

}
