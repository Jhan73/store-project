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
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Money;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The SQL of tickets and their lines; the rules that decide with it live in the services. */
@Component
class Tickets {

	record TicketRow(UUID id, long number, TicketType type, @Nullable UUID tableId, @Nullable String tableName,
			@Nullable String counterName, @Nullable String note, TicketStatus status, boolean billRequested,
			@Nullable UUID boardSourceId, int boardRounds, Currency currency, Instant createdAt, boolean quickSale) {
	}

	private static final String TICKET_COLUMNS = """
			k.id, k.number, k.type, k.table_id, t.name AS table_name, k.counter_name, k.note, k.status, k.bill_requested,
			k.board_source_id, k.board_rounds, k.currency, k.created_at, k.quick_sale
			FROM instore.ticket k LEFT JOIN instore.dining_table t ON t.id = k.table_id
			""";

	private static final String LINE_COLUMNS = """
			id, product_id, product_name, quantity, unit_price_amount, unit_price_currency, modifiers, note, allergy, state,
			sent_at, waste, void_reason_code, void_note, comp_reason_code, comp_note
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

	/** The lock every ticket command starts with; a ticket that is not open is rejected. */
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

	/**
	 * The lock a command on a closed sale starts with (voiding it).
	 *
	 * @throws BusinessException when the ticket is unknown or still open
	 */
	TicketRow lockClosed(UUID id, Instant now) {
		var locked = jdbc.sql("""
				UPDATE instore.ticket SET version = version + 1, updated_at = :now WHERE id = :id AND status = 'CLOSED'
				""").param("now", utc(now)).param("id", id).update();
		if (locked == 0) {
			var exists = find(id).isPresent();
			throw exists ? new BusinessException(InstoreError.TICKET_NOT_CLOSED, "Ticket " + id + " is not closed")
					: new BusinessException(InstoreError.TICKET_NOT_FOUND, "Ticket not found");
		}
		return require(id);
	}

	/** Closes a ticket this transaction holds the lock of; the table is free again from here on. */
	void close(UUID id, Instant now) {
		var closed = jdbc.sql("UPDATE instore.ticket SET status = 'CLOSED', closed_at = :now WHERE id = :id AND status = 'OPEN'")
			.param("now", utc(now))
			.param("id", id)
			.update();
		if (closed != 1) {
			throw new IllegalStateException("Ticket " + id + " changed state while it was locked");
		}
	}

	/** Ends a ticket this transaction holds the lock of without a sale; the table is free again from here on. */
	void cancel(UUID id, Instant now) {
		var cancelled = jdbc.sql("UPDATE instore.ticket SET status = 'CANCELLED', closed_at = :now WHERE id = :id AND status = 'OPEN'")
			.param("now", utc(now))
			.param("id", id)
			.update();
		if (cancelled != 1) {
			throw new IllegalStateException("Ticket " + id + " changed state while it was locked");
		}
	}

	void deleteUnsentLines(UUID ticketId) {
		jdbc.sql("DELETE FROM instore.ticket_line WHERE ticket_id = :ticket AND state = 'UNSENT'")
			.param("ticket", ticketId)
			.update();
	}

	/** @return false when the table already has an open ticket */
	boolean insert(UUID id, TicketType type, @Nullable UUID tableId, @Nullable String counterName, @Nullable String note,
			Currency currency, UUID openedBy, Instant now) {
		return insert(id, type, tableId, counterName, note, currency, openedBy, false, now);
	}

	/** @return false when the table already has an open ticket */
	boolean insert(UUID id, TicketType type, @Nullable UUID tableId, @Nullable String counterName, @Nullable String note,
			Currency currency, UUID openedBy, boolean quickSale, Instant now) {
		return jdbc.sql("""
				INSERT INTO instore.ticket (id, type, table_id, counter_name, note, status, currency, opened_by, quick_sale,
					created_at, updated_at)
				VALUES (:id, :type, :table, :counter, :note, 'OPEN', :currency, :openedBy, :quickSale, :now, :now)
				ON CONFLICT (table_id) WHERE status = 'OPEN' DO NOTHING
				""")
			.param("quickSale", quickSale)
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

	record TableRef(String name, boolean active) {
	}

	/** The table's name; it cannot be deactivated until this transaction ends. */
	String requireActiveTable(UUID tableId) {
		var table = jdbc.sql("SELECT name, active FROM instore.dining_table WHERE id = :id FOR SHARE")
			.param("id", tableId)
			.query((rs, row) -> new TableRef(rs.getString("name"), rs.getBoolean("active")))
			.optional()
			.orElseThrow(() -> new BusinessException(InstoreError.TABLE_NOT_FOUND, "Table not found"));
		if (!table.active()) {
			throw new BusinessException(InstoreError.TABLE_INACTIVE, "Table " + tableId + " is deactivated");
		}
		return table.name();
	}

	/** The unique index on open tickets per table rejects a table that is taken. */
	void moveToTable(UUID ticketId, UUID tableId) {
		jdbc.sql("UPDATE instore.ticket SET table_id = :table WHERE id = :id")
			.param("table", tableId)
			.param("id", ticketId)
			.update();
	}

	/** @return whether the flag changed */
	boolean setBillRequested(UUID ticketId, boolean requested) {
		return jdbc.sql("UPDATE instore.ticket SET bill_requested = :requested WHERE id = :id AND bill_requested <> :requested")
			.param("requested", requested)
			.param("id", ticketId)
			.update() == 1;
	}

	/** Every card the ticket ever fed: one per round. */
	List<UUID> boardSources(UUID ticketId) {
		return jdbc.sql("""
				SELECT board_source_id FROM instore.ticket WHERE id = :id AND board_source_id IS NOT NULL
				UNION SELECT board_source_id FROM instore.ticket_line WHERE ticket_id = :id AND board_source_id IS NOT NULL
				""").param("id", ticketId).query(UUID.class).list();
	}

	/** Takes the lock every ticket command starts with on the table's open ticket, if it has one. */
	Optional<UUID> lockOpenTicketOn(UUID tableId, Instant now) {
		return jdbc.sql("""
				UPDATE instore.ticket SET version = version + 1, updated_at = :now
				WHERE table_id = :table AND status = 'OPEN' RETURNING id
				""").param("now", utc(now)).param("table", tableId).query(UUID.class).optional();
	}

	/** Blocks tickets opening or moving onto the table until this transaction ends. */
	void lockTable(UUID tableId) {
		jdbc.sql("SELECT 1 FROM instore.dining_table WHERE id = :id FOR UPDATE").param("id", tableId).query().listOfRows();
	}

	boolean hasOpenTicketOn(UUID tableId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM instore.ticket WHERE table_id = :id AND status = 'OPEN')")
			.param("id", tableId)
			.query(Boolean.class)
			.single();
	}

	List<TicketSummary> open(int limit, long offset) {
		return jdbc.sql("""
				SELECT k.id, k.number, k.type, k.table_id, t.name AS table_name, k.counter_name, k.bill_requested,
					k.currency, k.created_at,
					coalesce((SELECT sum(l.unit_price_amount * l.quantity) FROM instore.ticket_line l
						WHERE l.ticket_id = k.id AND l.state IN ('UNSENT', 'SENT')), 0) AS total
				FROM instore.ticket k LEFT JOIN instore.dining_table t ON t.id = k.table_id
				WHERE k.status = 'OPEN' ORDER BY k.created_at, k.id LIMIT :limit OFFSET :offset
				""").param("limit", limit).param("offset", offset).query((rs, row) -> summary(rs)).list();
	}

	long countOpen() {
		return jdbc.sql("SELECT count(*) FROM instore.ticket WHERE status = 'OPEN'").query(Long.class).single();
	}

	private static final String CLOSED_FILTER = """
			WHERE k.status = 'CLOSED'
				AND (CAST(:from AS timestamptz) IS NULL OR k.closed_at >= CAST(:from AS timestamptz))
				AND (CAST(:to AS timestamptz) IS NULL OR k.closed_at < CAST(:to AS timestamptz))
				AND (CAST(:table AS uuid) IS NULL OR k.table_id = CAST(:table AS uuid))
				AND (CAST(:type AS varchar) IS NULL OR k.type = CAST(:type AS varchar))
				AND (CAST(:cashier AS uuid) IS NULL OR EXISTS (SELECT 1 FROM instore.in_store_payment p
					WHERE p.ticket_id = k.id AND p.recorded_by = CAST(:cashier AS uuid)
						AND NOT EXISTS (SELECT 1 FROM instore.payment_void v WHERE v.payment_id = p.id)))
			""";

	List<ClosedTicketSummary> closed(ClosedTicketFilter filter, DayRange range, int limit, long offset) {
		return closedParams(jdbc.sql("""
				SELECT k.id, k.number, k.type, k.table_id, t.name AS table_name, k.counter_name, k.quick_sale, k.currency,
					k.closed_at,
					coalesce((SELECT sum(l.unit_price_amount * l.quantity) FROM instore.ticket_line l
						WHERE l.ticket_id = k.id AND l.state IN ('UNSENT', 'SENT')), 0) AS total,
					EXISTS (SELECT 1 FROM instore.sale_void v WHERE v.ticket_id = k.id) AS voided,
					(SELECT p.recorded_by FROM instore.in_store_payment p WHERE p.ticket_id = k.id
							AND NOT EXISTS (SELECT 1 FROM instore.payment_void v WHERE v.payment_id = p.id)
						ORDER BY p.recorded_at DESC, p.id DESC LIMIT 1) AS cashier_id
				FROM instore.ticket k LEFT JOIN instore.dining_table t ON t.id = k.table_id
				""" + CLOSED_FILTER + " ORDER BY k.closed_at DESC, k.id DESC LIMIT :limit OFFSET :offset"), filter, range)
			.param("limit", limit)
			.param("offset", offset)
			.query((rs, row) -> closedSummary(rs))
			.list();
	}

	long countClosed(ClosedTicketFilter filter, DayRange range) {
		return closedParams(jdbc.sql("SELECT count(*) FROM instore.ticket k " + CLOSED_FILTER), filter, range)
			.query(Long.class)
			.single();
	}

	private static JdbcClient.StatementSpec closedParams(JdbcClient.StatementSpec spec, ClosedTicketFilter filter,
			DayRange range) {
		return spec.param("from", range.from() == null ? null : utc(range.from()))
			.param("to", range.to() == null ? null : utc(range.to()))
			.param("table", filter.tableId())
			.param("type", filter.type() == null ? null : filter.type().name())
			.param("cashier", filter.cashierId());
	}

	void setNote(UUID ticketId, @Nullable String note) {
		jdbc.sql("UPDATE instore.ticket SET note = :note WHERE id = :id").param("note", note).param("id", ticketId).update();
	}

	/** The unsent lines in the order they were added, as the board takes them. */
	List<BoardLineIntake> unsentForBoard(UUID ticketId) {
		return jdbc.sql("""
				SELECT id, product_id, product_name, station_id, quantity, modifiers, note, allergy FROM instore.ticket_line
				WHERE ticket_id = :ticket AND state = 'UNSENT' ORDER BY position
				""").param("ticket", ticketId).query((rs, row) -> {
			var options = json.readValue(rs.getString("modifiers"), OPTIONS);
			return new BoardLineIntake(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
					rs.getString("product_name"), rs.getInt("quantity"), TicketRules.modifiersText(options),
					options.stream().map(ChosenOption::optionId).toList(), rs.getString("note"), rs.getBoolean("allergy"),
					rs.getObject("station_id", UUID.class));
		}).list();
	}

	void markSent(UUID ticketId, List<UUID> lineIds, UUID boardSourceId, Instant now) {
		var sent = jdbc.sql("""
				UPDATE instore.ticket_line
				SET state = 'SENT', board_source_id = :source, sent_at = :now, version = version + 1, updated_at = :now
				WHERE ticket_id = :ticket AND state = 'UNSENT' AND id IN (:ids)
				""")
			.param("source", boardSourceId)
			.param("now", utc(now))
			.param("ticket", ticketId)
			.param("ids", lineIds)
			.update();
		if (sent != lineIds.size()) {
			throw new IllegalStateException("Ticket " + ticketId + " changed its unsent lines while it was locked");
		}
	}

	void startRound(UUID ticketId, UUID boardSourceId, int rounds) {
		jdbc.sql("UPDATE instore.ticket SET board_source_id = :source, board_rounds = :rounds WHERE id = :id")
			.param("source", boardSourceId)
			.param("rounds", rounds)
			.param("id", ticketId)
			.update();
	}

	/** The card a sent line lives on, which is not always the ticket's current one. */
	UUID boardSourceOf(UUID lineId) {
		return jdbc.sql("SELECT board_source_id FROM instore.ticket_line WHERE id = :id")
			.param("id", lineId)
			.query(UUID.class)
			.single();
	}

	void markVoided(UUID ticketId, UUID lineId, boolean waste, String reasonCode, @Nullable String note, UUID actor,
			Instant now) {
		var voided = jdbc.sql("""
				UPDATE instore.ticket_line
				SET state = 'VOIDED', waste = :waste, void_reason_code = :reason, void_note = :note, voided_by = :actor,
					voided_at = :now, version = version + 1, updated_at = :now
				WHERE id = :id AND ticket_id = :ticket AND state = 'SENT'
				""")
			.param("waste", waste)
			.param("reason", reasonCode)
			.param("note", note)
			.param("actor", actor)
			.param("now", utc(now))
			.param("id", lineId)
			.param("ticket", ticketId)
			.update();
		if (voided != 1) {
			throw new IllegalStateException("Line " + lineId + " changed state while its ticket was locked");
		}
	}

	void markComped(UUID ticketId, UUID lineId, String reasonCode, @Nullable String note, UUID actor, Instant now) {
		var comped = jdbc.sql("""
				UPDATE instore.ticket_line
				SET state = 'COMPED', waste = true, comp_reason_code = :reason, comp_note = :note, comped_by = :actor,
					comped_at = :now, version = version + 1, updated_at = :now
				WHERE id = :id AND ticket_id = :ticket AND state = 'SENT'
				""")
			.param("reason", reasonCode)
			.param("note", note)
			.param("actor", actor)
			.param("now", utc(now))
			.param("id", lineId)
			.param("ticket", ticketId)
			.update();
		if (comped != 1) {
			throw new IllegalStateException("Line " + lineId + " changed state while its ticket was locked");
		}
	}

	Optional<TicketLineView> line(UUID ticketId, UUID lineId) {
		return jdbc.sql("SELECT " + LINE_COLUMNS + " FROM instore.ticket_line WHERE id = :id AND ticket_id = :ticket")
			.param("id", lineId)
			.param("ticket", ticketId)
			.query((rs, row) -> line(rs))
			.optional();
	}

	TicketLineView requireLine(UUID ticketId, UUID lineId) {
		return line(ticketId, lineId)
			.orElseThrow(() -> new BusinessException(InstoreError.LINE_NOT_FOUND, "The ticket has no such line"));
	}

	/** The lines that still count on the bill or the board: everything but the voided ones. */
	int liveLineCount(UUID ticketId) {
		return jdbc.sql("SELECT count(*) FROM instore.ticket_line WHERE ticket_id = :id AND state <> 'VOIDED'")
			.param("id", ticketId)
			.query(Integer.class)
			.single();
	}

	int nextPosition(UUID ticketId) {
		return jdbc.sql("SELECT coalesce(max(position) + 1, 0) FROM instore.ticket_line WHERE ticket_id = :id")
			.param("id", ticketId)
			.query(Integer.class)
			.single();
	}

	UUID insertLine(UUID ticketId, int position, LinePrice price, LineDraft draft, Instant now) {
		var id = Ids.newId();
		jdbc.sql("""
				INSERT INTO instore.ticket_line (id, ticket_id, position, product_id, product_name, station_id, quantity,
					unit_price_amount, unit_price_currency, modifiers, note, allergy, state, created_at, updated_at)
				VALUES (:id, :ticket, :position, :product, :name, :station, :quantity, :amount, :currency,
					CAST(:modifiers AS jsonb), :note, :allergy, 'UNSENT', :now, :now)
				""")
			.param("id", id)
			.param("ticket", ticketId)
			.param("position", position)
			.param("product", price.productId())
			.param("name", price.productName())
			.param("station", price.stationId())
			.param("quantity", draft.quantity())
			.param("amount", price.unitPrice().amount())
			.param("currency", price.unitPrice().currency().getCurrencyCode())
			.param("modifiers", json.writeValueAsString(price.options()))
			.param("note", draft.note())
			.param("allergy", draft.allergy())
			.param("now", utc(now))
			.update();
		return id;
	}

	/** @return false when the line is not unsent (or not on the ticket) */
	boolean replaceUnsentLine(UUID ticketId, UUID lineId, LinePrice price, LineDraft draft, Instant now) {
		return jdbc.sql("""
				UPDATE instore.ticket_line
				SET product_id = :product, product_name = :name, station_id = :station, quantity = :quantity,
					unit_price_amount = :amount, unit_price_currency = :currency, modifiers = CAST(:modifiers AS jsonb),
					note = :note, allergy = :allergy, version = version + 1, updated_at = :now
				WHERE id = :id AND ticket_id = :ticket AND state = 'UNSENT'
				""")
			.param("id", lineId)
			.param("ticket", ticketId)
			.param("product", price.productId())
			.param("name", price.productName())
			.param("station", price.stationId())
			.param("quantity", draft.quantity())
			.param("amount", price.unitPrice().amount())
			.param("currency", price.unitPrice().currency().getCurrencyCode())
			.param("modifiers", json.writeValueAsString(price.options()))
			.param("note", draft.note())
			.param("allergy", draft.allergy())
			.param("now", utc(now))
			.update() == 1;
	}

	/** @return false when the line is not unsent (or not on the ticket) */
	boolean deleteUnsentLine(UUID ticketId, UUID lineId) {
		return jdbc.sql("DELETE FROM instore.ticket_line WHERE id = :id AND ticket_id = :ticket AND state = 'UNSENT'")
			.param("id", lineId)
			.param("ticket", ticketId)
			.update() == 1;
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
				rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getBoolean("quick_sale"));
	}

	private TicketSummary summary(ResultSet rs) throws SQLException {
		var currency = Currency.getInstance(rs.getString("currency").trim());
		return new TicketSummary(rs.getObject("id", UUID.class), rs.getLong("number"),
				TicketType.valueOf(rs.getString("type")), rs.getObject("table_id", UUID.class), rs.getString("table_name"),
				rs.getString("counter_name"), rs.getBoolean("bill_requested"),
				new Money(rs.getBigDecimal("total"), currency), rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	private ClosedTicketSummary closedSummary(ResultSet rs) throws SQLException {
		var currency = Currency.getInstance(rs.getString("currency").trim());
		return new ClosedTicketSummary(rs.getObject("id", UUID.class), rs.getLong("number"),
				TicketType.valueOf(rs.getString("type")), rs.getObject("table_id", UUID.class), rs.getString("table_name"),
				rs.getString("counter_name"), rs.getBoolean("quick_sale"), new Money(rs.getBigDecimal("total"), currency),
				rs.getObject("closed_at", OffsetDateTime.class).toInstant(), rs.getBoolean("voided"),
				rs.getObject("cashier_id", UUID.class));
	}

	private TicketLineView line(ResultSet rs) throws SQLException {
		var currency = Currency.getInstance(rs.getString("unit_price_currency").trim());
		var sentAt = rs.getObject("sent_at", OffsetDateTime.class);
		return new TicketLineView(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
				rs.getString("product_name"), rs.getInt("quantity"), new Money(rs.getBigDecimal("unit_price_amount"), currency),
				json.readValue(rs.getString("modifiers"), OPTIONS), rs.getString("note"), rs.getBoolean("allergy"),
				TicketLineState.valueOf(rs.getString("state")), sentAt == null ? null : sentAt.toInstant(),
				rs.getBoolean("waste"), rs.getString("void_reason_code"), rs.getString("void_note"),
				rs.getString("comp_reason_code"), rs.getString("comp_note"));
	}

}
