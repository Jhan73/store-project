package com.jhanantezana.jugueria.preparation.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardLineState;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Ids;

/** The SQL of the board; the conditional updates that decide races live in the commands, next to their rules. */
@Component
class BoardStore {

	record OrderRef(UUID id, BoardSource source, UUID sourceId, BoardOrderStatus status) {
	}

	private static final String ORDER_COLUMNS = """
			o.id, o.source, o.source_id, o.short_number, o.channel, o.label, o.status, o.fire_at, o.sent_at, o.started_at,
			o.ready_at, o.handed_over_at
			""";

	private final JdbcClient jdbc;

	BoardStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	Optional<OrderRef> find(UUID id) {
		return jdbc.sql("SELECT id, source, source_id, status FROM preparation.board_order WHERE id = :id")
			.param("id", id)
			.query((rs, row) -> ref(rs))
			.optional();
	}

	Optional<OrderRef> find(BoardSource source, UUID sourceId) {
		return jdbc.sql("""
				SELECT id, source, source_id, status FROM preparation.board_order
				WHERE source = :source AND source_id = :sourceId
				""").param("source", source.name()).param("sourceId", sourceId).query((rs, row) -> ref(rs)).optional();
	}

	/**
	 * Takes the card's row lock when the card is in one of the allowed statuses. Every command that touches lines runs
	 * this first: later statements then see everything committed before the lock, so "is any line still pending" is
	 * answered against the final state. Lock before reading what the decision depends on.
	 */
	Optional<OrderRef> lock(UUID id, Instant now, BoardOrderStatus... allowed) {
		return jdbc.sql("""
				UPDATE preparation.board_order SET version = version + 1, updated_at = :now
				WHERE id = :id AND status IN (:allowed)
				RETURNING id, source, source_id, status
				""")
			.param("now", utc(now))
			.param("id", id)
			.param("allowed", java.util.Arrays.stream(allowed).map(Enum::name).toList())
			.query((rs, row) -> ref(rs))
			.optional();
	}

	/**
	 * Why a command found its card in the wrong status. Read after the conditional update affected nothing, so it only
	 * picks the message: the update itself already decided the race.
	 */
	BusinessException rejection(UUID orderId, Function<BoardOrderStatus, PreparationError> byStatus) {
		var current = find(orderId);
		if (current.isEmpty()) {
			return new BusinessException(PreparationError.ORDER_NOT_FOUND, "The board order does not exist");
		}
		var status = current.get().status();
		var error = status == BoardOrderStatus.CANCELLED ? PreparationError.ORDER_CANCELLED : byStatus.apply(status);
		return new BusinessException(error, "Board order " + orderId + " is " + status);
	}

	/** Runs a statement that ends in {@code RETURNING id, source, source_id, status}. */
	Optional<OrderRef> refOf(JdbcClient.StatementSpec statement) {
		return statement.query((rs, row) -> ref(rs)).optional();
	}

	Optional<UUID> orderOfLine(UUID lineId) {
		return jdbc.sql("SELECT board_order_id FROM preparation.board_line WHERE id = :id")
			.param("id", lineId)
			.query(UUID.class)
			.optional();
	}

	/** @return false when the source is already on the board */
	boolean insertOrder(UUID id, BoardOrderIntake intake, Instant now) {
		return jdbc.sql("""
				INSERT INTO preparation.board_order (id, source, source_id, short_number, channel, label, status, fire_at,
					sent_at, created_at, updated_at)
				VALUES (:id, :source, :sourceId, :shortNumber, :channel, :label, 'WAITING', :fireAt, :sentAt, :now, :now)
				ON CONFLICT (source, source_id) DO NOTHING
				""")
			.param("id", id)
			.param("source", intake.source().name())
			.param("sourceId", intake.sourceId())
			.param("shortNumber", intake.shortNumber())
			.param("channel", intake.channel().name())
			.param("label", intake.label())
			.param("fireAt", intake.fireAt() == null ? null : utc(intake.fireAt()))
			.param("sentAt", utc(intake.sentAt()))
			.param("now", utc(now))
			.update() == 1;
	}

	/** @return the new line's id, or empty when the order already has a line from the same source line */
	Optional<UUID> insertLine(UUID orderId, int position, BoardLineIntake line, Instant now) {
		return jdbc.sql("""
				INSERT INTO preparation.board_line (id, board_order_id, source_line_id, position, product_id, display_name,
					quantity, modifiers_text, modifier_signature, note, allergy, station_id, state, created_at, updated_at)
				VALUES (:id, :order, :sourceLine, :position, :product, :name, :quantity, :modifiersText, :signature, :note,
					:allergy, :station, 'PENDING', :now, :now)
				ON CONFLICT (board_order_id, source_line_id) DO NOTHING
				RETURNING id
				""")
			.param("id", Ids.newId())
			.param("order", orderId)
			.param("sourceLine", line.sourceLineId())
			.param("position", position)
			.param("product", line.productId())
			.param("name", line.displayName())
			.param("quantity", line.quantity())
			.param("modifiersText", line.modifiersText())
			.param("signature", ModifierSignature.of(line.modifierOptionIds()))
			.param("note", line.note())
			.param("allergy", line.allergy())
			.param("station", line.stationId())
			.param("now", utc(now))
			.query(UUID.class)
			.optional();
	}

	int nextPosition(UUID orderId) {
		return jdbc.sql("SELECT coalesce(max(position) + 1, 0) FROM preparation.board_line WHERE board_order_id = :id")
			.param("id", orderId)
			.query(Integer.class)
			.single();
	}

	void insertMarker(UUID orderId, @Nullable UUID lineId, MarkerKind kind, Instant now) {
		jdbc.sql("""
				INSERT INTO preparation.board_marker (id, board_order_id, line_id, kind, created_at)
				VALUES (:id, :order, :line, :kind, :now)
				""")
			.param("id", Ids.newId())
			.param("order", orderId)
			.param("line", lineId)
			.param("kind", kind.name())
			.param("now", utc(now))
			.update();
	}

	Optional<MarkerView> marker(UUID id) {
		return jdbc.sql("""
				SELECT id, board_order_id, line_id, kind, created_at, acknowledged_at FROM preparation.board_marker
				WHERE id = :id
				""").param("id", id).query((rs, row) -> marker(rs)).optional();
	}

	/** Cards matching the filter, with their lines and unacknowledged markers; the filter is appended to a WHERE. */
	List<BoardOrderView> cards(String filter, Map<String, Object> params, String orderBy, @Nullable UUID station) {
		var query = jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM preparation.board_order o WHERE " + filter + " ORDER BY "
				+ orderBy);
		for (var param : params.entrySet()) {
			query = query.param(param.getKey(), param.getValue());
		}
		var rows = query.query((rs, row) -> orderRow(rs)).list();
		return assemble(rows, station);
	}

	private List<BoardOrderView> assemble(List<OrderRow> rows, @Nullable UUID station) {
		if (rows.isEmpty()) {
			return List.of();
		}
		var ids = rows.stream().map(OrderRow::id).toList();
		var markers = openMarkers(ids);
		var voided = markers.values()
			.stream()
			.flatMap(List::stream)
			.filter(marker -> marker.kind() == MarkerKind.VOID && marker.lineId() != null)
			.map(MarkerView::lineId)
			.collect(Collectors.toSet());
		var lines = lines(ids, station);
		var cards = new ArrayList<BoardOrderView>();
		for (var row : rows) {
			// A voided line stays on the card only while its VOID label is waiting to be acknowledged.
			var shown = lines.getOrDefault(row.id(), List.of())
				.stream()
				.filter(line -> line.state() != BoardLineState.VOIDED || voided.contains(line.id()))
				.toList();
			cards.add(row.toView(shown, markers.getOrDefault(row.id(), List.of())));
		}
		return cards;
	}

	private Map<UUID, List<BoardLineView>> lines(Collection<UUID> orderIds, @Nullable UUID station) {
		var sql = """
				SELECT id, board_order_id, product_id, display_name, quantity, modifiers_text, note, allergy, station_id,
					state, waste, flagged_unavailable, ready_at
				FROM preparation.board_line WHERE board_order_id IN (:ids)
				""" + (station == null ? "" : " AND station_id = :station") + " ORDER BY board_order_id, position";
		var query = jdbc.sql(sql).param("ids", orderIds);
		if (station != null) {
			query = query.param("station", station);
		}
		var byOrder = new LinkedHashMap<UUID, List<BoardLineView>>();
		query.query(rs -> {
			byOrder.computeIfAbsent(rs.getObject("board_order_id", UUID.class), id -> new ArrayList<>()).add(line(rs));
		});
		return byOrder;
	}

	private Map<UUID, List<MarkerView>> openMarkers(Collection<UUID> orderIds) {
		var byOrder = new LinkedHashMap<UUID, List<MarkerView>>();
		jdbc.sql("""
				SELECT id, board_order_id, line_id, kind, created_at, acknowledged_at FROM preparation.board_marker
				WHERE board_order_id IN (:ids) AND acknowledged_at IS NULL ORDER BY created_at, id
				""").param("ids", orderIds).query(rs -> {
			var marker = marker(rs);
			byOrder.computeIfAbsent(marker.boardOrderId(), id -> new ArrayList<>()).add(marker);
		});
		return byOrder;
	}

	private record OrderRow(UUID id, BoardSource source, UUID sourceId, String shortNumber, BoardChannel channel,
			@Nullable String label, BoardOrderStatus status, @Nullable Instant fireAt, Instant sentAt,
			@Nullable Instant startedAt, @Nullable Instant readyAt, @Nullable Instant handedOverAt) {

		BoardOrderView toView(List<BoardLineView> lines, List<MarkerView> markers) {
			return new BoardOrderView(id, source, sourceId, shortNumber, channel, label, status, sentAt, fireAt,
					startedAt, readyAt, handedOverAt, lines, markers);
		}

	}

	private static OrderRow orderRow(ResultSet rs) throws SQLException {
		return new OrderRow(rs.getObject("id", UUID.class), BoardSource.valueOf(rs.getString("source")),
				rs.getObject("source_id", UUID.class), rs.getString("short_number"),
				BoardChannel.valueOf(rs.getString("channel")), rs.getString("label"),
				BoardOrderStatus.valueOf(rs.getString("status")), instant(rs, "fire_at"), instant(rs, "sent_at"),
				instant(rs, "started_at"), instant(rs, "ready_at"), instant(rs, "handed_over_at"));
	}

	private static BoardLineView line(ResultSet rs) throws SQLException {
		return new BoardLineView(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
				rs.getString("display_name"), rs.getInt("quantity"), rs.getString("modifiers_text"),
				rs.getString("note"), rs.getBoolean("allergy"), rs.getObject("station_id", UUID.class),
				BoardLineState.valueOf(rs.getString("state")), rs.getBoolean("waste"),
				rs.getBoolean("flagged_unavailable"), instant(rs, "ready_at"));
	}

	private static MarkerView marker(ResultSet rs) throws SQLException {
		return new MarkerView(rs.getObject("id", UUID.class), rs.getObject("board_order_id", UUID.class),
				rs.getObject("line_id", UUID.class), MarkerKind.valueOf(rs.getString("kind")),
				instant(rs, "created_at"), instant(rs, "acknowledged_at"));
	}

	private static OrderRef ref(ResultSet rs) throws SQLException {
		return new OrderRef(rs.getObject("id", UUID.class), BoardSource.valueOf(rs.getString("source")),
				rs.getObject("source_id", UUID.class), BoardOrderStatus.valueOf(rs.getString("status")));
	}

	private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
		var value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
