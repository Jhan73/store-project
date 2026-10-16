package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.PREPARING;
import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.WAITING;
import static com.jhanantezana.jugueria.preparation.internal.BoardStore.utc;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.LineFlaggedUnavailable;
import com.jhanantezana.jugueria.preparation.LineReady;
import com.jhanantezana.jugueria.preparation.OrderHandedOver;
import com.jhanantezana.jugueria.preparation.OrderReady;
import com.jhanantezana.jugueria.preparation.OrderRecalled;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.preparation.PreparationStarted;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;

/** What staff do on the board; each command is decided by a conditional update on the card's row. */
@Service
public class BoardCommands {

	private final BoardStore store;

	private final BoardQueries queries;

	private final BoardEvents boardEvents;

	private final ApplicationEventPublisher events;

	private final JdbcClient jdbc;

	private final Clock clock;

	private final CurrentActor currentActor;

	private final PreparationProperties properties;

	BoardCommands(BoardStore store, BoardQueries queries, BoardEvents boardEvents, ApplicationEventPublisher events,
			JdbcClient jdbc, Clock clock, CurrentActor currentActor, PreparationProperties properties) {
		this.store = store;
		this.queries = queries;
		this.boardEvents = boardEvents;
		this.events = events;
		this.jdbc = jdbc;
		this.clock = clock;
		this.currentActor = currentActor;
		this.properties = properties;
	}

	@Transactional
	public BoardOrderView start(UUID orderId) {
		var now = Instant.now(clock);
		var order = store.refOf(jdbc.sql("""
				UPDATE preparation.board_order
				SET status = 'PREPARING', started_at = :now, version = version + 1, updated_at = :now
				WHERE id = :id AND status = 'WAITING'
				RETURNING id, source, source_id, status
				""").param("now", utc(now)).param("id", orderId))
			.orElseThrow(() -> store.rejection(orderId, status -> PreparationError.ORDER_ALREADY_STARTED));
		events.publishEvent(new PreparationStarted(orderId, order.source(), order.sourceId(), now));
		boardEvents.changed("ORDER_STARTED", orderId, null, now);
		return queries.card(orderId);
	}

	@Transactional
	public BoardOrderView readyLine(UUID lineId) {
		var now = Instant.now(clock);
		var orderId = requireOrderOf(lineId);
		var order = store.lock(orderId, now, PREPARING)
			.orElseThrow(() -> store.rejection(orderId,
					status -> status == WAITING ? PreparationError.ORDER_NOT_PREPARING : PreparationError.LINE_NOT_PENDING));
		var sourceLine = jdbc.sql("""
				UPDATE preparation.board_line
				SET state = 'READY', ready_at = :now, version = version + 1, updated_at = :now
				WHERE id = :id AND state = 'PENDING' AND NOT flagged_unavailable
				RETURNING source_line_id
				""").param("now", utc(now)).param("id", lineId).query(UUID.class).optional();
		if (sourceLine.isEmpty()) {
			throw lineRejection(lineId, PreparationError.LINE_FLAGGED_UNAVAILABLE);
		}
		events.publishEvent(new LineReady(orderId, lineId, sourceLine.get(), order.source(), order.sourceId(), now));
		boardEvents.changed("LINE_READY", orderId, lineId, now);
		var promoted = jdbc.sql("""
				UPDATE preparation.board_order SET status = 'READY', ready_at = :now, updated_at = :now
				WHERE id = :id AND status = 'PREPARING'
					AND NOT EXISTS (SELECT 1 FROM preparation.board_line WHERE board_order_id = :id AND state = 'PENDING')
				""").param("now", utc(now)).param("id", orderId).update();
		if (promoted == 1) {
			events.publishEvent(new OrderReady(orderId, order.source(), order.sourceId(), now));
			boardEvents.changed("ORDER_READY", orderId, null, now);
		}
		return queries.card(orderId);
	}

	@Transactional
	public BoardOrderView readyOrder(UUID orderId) {
		var now = Instant.now(clock);
		var order = store.lock(orderId, now, PREPARING)
			.orElseThrow(() -> store.rejection(orderId, status -> PreparationError.ORDER_NOT_PREPARING));
		// A line added since the screen loaded still carries its unacknowledged label, so staff have not seen it.
		var unseen = jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM preparation.board_marker m JOIN preparation.board_line l ON l.id = m.line_id
					WHERE l.board_order_id = :id AND l.state = 'PENDING' AND m.kind = 'MODIFIED' AND m.acknowledged_at IS NULL)
				""").param("id", orderId).query(Boolean.class).single();
		if (unseen) {
			throw new BusinessException(PreparationError.LINES_NOT_ACKNOWLEDGED,
					"Board order " + orderId + " has added lines that are not acknowledged yet");
		}
		var ready = jdbc.sql("""
				UPDATE preparation.board_order SET status = 'READY', ready_at = :now, updated_at = :now
				WHERE id = :id AND status = 'PREPARING'
					AND NOT EXISTS (SELECT 1 FROM preparation.board_line
						WHERE board_order_id = :id AND state = 'PENDING' AND flagged_unavailable)
				""").param("now", utc(now)).param("id", orderId).update();
		if (ready == 0) {
			throw new BusinessException(PreparationError.LINE_FLAGGED_UNAVAILABLE,
					"Board order " + orderId + " has a line flagged unavailable; resolve it first");
		}
		var finished = jdbc.sql("""
				UPDATE preparation.board_line
				SET state = 'READY', ready_at = :now, version = version + 1, updated_at = :now
				WHERE board_order_id = :id AND state = 'PENDING'
				RETURNING id, source_line_id
				""")
			.param("now", utc(now))
			.param("id", orderId)
			.query((rs, row) -> new LineIds(rs.getObject("id", UUID.class), rs.getObject("source_line_id", UUID.class)))
			.list();
		for (var line : finished) {
			events.publishEvent(new LineReady(orderId, line.id(), line.sourceId(), order.source(), order.sourceId(), now));
		}
		events.publishEvent(new OrderReady(orderId, order.source(), order.sourceId(), now));
		boardEvents.changed("ORDER_READY", orderId, null, now);
		return queries.card(orderId);
	}

	@Transactional
	public BoardOrderView handover(UUID orderId) {
		var now = Instant.now(clock);
		var order = store.refOf(jdbc.sql("""
				UPDATE preparation.board_order
				SET status = 'HANDED_OVER', handed_over_at = :now, version = version + 1, updated_at = :now
				WHERE id = :id AND status = 'READY'
				RETURNING id, source, source_id, status
				""").param("now", utc(now)).param("id", orderId))
			.orElseThrow(() -> store.rejection(orderId, status -> PreparationError.ORDER_NOT_READY));
		events.publishEvent(new OrderHandedOver(orderId, order.source(), order.sourceId(), now));
		boardEvents.changed("ORDER_HANDED_OVER", orderId, null, now);
		return queries.card(orderId);
	}

	/** Brings a ready or handed-over order back to preparing, with its finished lines pending again. */
	@Transactional
	public BoardOrderView recall(UUID orderId) {
		var now = Instant.now(clock);
		var since = now.minus(properties.recallWindow());
		var order = store.refOf(jdbc.sql("""
				UPDATE preparation.board_order
				SET status = 'PREPARING', ready_at = NULL, handed_over_at = NULL, version = version + 1, updated_at = :now
				WHERE id = :id AND status IN ('READY', 'HANDED_OVER') AND coalesce(handed_over_at, ready_at) >= :since
				RETURNING id, source, source_id, status
				""").param("now", utc(now)).param("since", utc(since)).param("id", orderId))
			.orElseThrow(() -> store.rejection(orderId,
					status -> status == BoardOrderStatus.READY || status == BoardOrderStatus.HANDED_OVER
							? PreparationError.RECALL_WINDOW_EXPIRED : PreparationError.ORDER_NOT_COMPLETED));
		jdbc.sql("""
				UPDATE preparation.board_line
				SET state = 'PENDING', ready_at = NULL, version = version + 1, updated_at = :now
				WHERE board_order_id = :id AND state = 'READY'
				""").param("now", utc(now)).param("id", orderId).update();
		events.publishEvent(new OrderRecalled(orderId, order.source(), order.sourceId(), now));
		boardEvents.changed("ORDER_RECALLED", orderId, null, now);
		return queries.card(orderId);
	}

	/** Flags a line whose item ran out; the order cannot be ready until the line is voided or replaced. */
	@Transactional
	public BoardOrderView flagLine(UUID lineId) {
		var now = Instant.now(clock);
		var orderId = requireOrderOf(lineId);
		var order = store.lock(orderId, now, WAITING, PREPARING)
			.orElseThrow(() -> store.rejection(orderId, status -> PreparationError.LINE_NOT_PENDING));
		var sourceLine = jdbc.sql("""
				UPDATE preparation.board_line SET flagged_unavailable = true, version = version + 1, updated_at = :now
				WHERE id = :id AND state = 'PENDING' AND NOT flagged_unavailable
				RETURNING source_line_id
				""").param("now", utc(now)).param("id", lineId).query(UUID.class).optional();
		if (sourceLine.isEmpty()) {
			throw lineRejection(lineId, PreparationError.LINE_ALREADY_FLAGGED);
		}
		events.publishEvent(new LineFlaggedUnavailable(orderId, lineId, sourceLine.get(), order.source(),
				order.sourceId(), currentActor.id(), currentActor.role(), now));
		boardEvents.changed("LINE_FLAGGED", orderId, lineId, now);
		return queries.card(orderId);
	}

	@Transactional
	public MarkerView acknowledge(UUID markerId, UUID actorId) {
		var now = Instant.now(clock);
		var acknowledged = jdbc.sql("""
				UPDATE preparation.board_marker SET acknowledged_by = :actor, acknowledged_at = :now
				WHERE id = :id AND acknowledged_at IS NULL
				RETURNING board_order_id, line_id
				""")
			.param("actor", actorId)
			.param("now", utc(now))
			.param("id", markerId)
			.query((rs, row) -> new MarkerOwner(rs.getObject("board_order_id", UUID.class),
					rs.getObject("line_id", UUID.class)))
			.optional();
		if (acknowledged.isEmpty()) {
			throw store.marker(markerId).isEmpty()
					? new BusinessException(PreparationError.MARKER_NOT_FOUND, "The marker does not exist")
					: new BusinessException(PreparationError.MARKER_ALREADY_ACKNOWLEDGED,
							"Marker " + markerId + " was already acknowledged");
		}
		boardEvents.changed("MARKER_ACKNOWLEDGED", acknowledged.get().orderId(), acknowledged.get().lineId(), now);
		return queries.marker(markerId);
	}

	private UUID requireOrderOf(UUID lineId) {
		return store.orderOfLine(lineId)
			.orElseThrow(() -> new BusinessException(PreparationError.LINE_NOT_FOUND, "The board line does not exist"));
	}

	// Read after the conditional update affected nothing: it only picks the message.
	private BusinessException lineRejection(UUID lineId, PreparationError whenFlagged) {
		var pendingAndFlagged = jdbc.sql("""
				SELECT state = 'PENDING' AND flagged_unavailable FROM preparation.board_line WHERE id = :id
				""").param("id", lineId).query(Boolean.class).single();
		return pendingAndFlagged ? new BusinessException(whenFlagged, "Board line " + lineId + " is flagged unavailable")
				: new BusinessException(PreparationError.LINE_NOT_PENDING, "Board line " + lineId + " is not pending");
	}

	private record LineIds(UUID id, UUID sourceId) {
	}

	private record MarkerOwner(UUID orderId, UUID lineId) {
	}

}
