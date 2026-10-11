package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.PREPARING;
import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.READY;
import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.WAITING;
import static com.jhanantezana.jugueria.preparation.internal.BoardStore.utc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.OrderReady;
import com.jhanantezana.jugueria.preparation.OrderRecalled;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Ids;

@Service
class PreparationApiImpl implements PreparationApi {

	private final BoardStore store;

	private final BoardEvents boardEvents;

	private final ApplicationEventPublisher events;

	private final JdbcClient jdbc;

	private final Clock clock;

	PreparationApiImpl(BoardStore store, BoardEvents boardEvents, ApplicationEventPublisher events, JdbcClient jdbc,
			Clock clock) {
		this.store = store;
		this.boardEvents = boardEvents;
		this.events = events;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional
	public UUID accept(BoardOrderIntake intake) {
		var now = Instant.now(clock);
		var id = Ids.newId();
		if (!store.insertOrder(id, intake, now)) {
			return store.find(intake.source(), intake.sourceId()).orElseThrow().id();
		}
		var position = 0;
		for (var line : intake.lines()) {
			store.insertLine(id, position++, line, now);
		}
		store.insertMarker(id, null, MarkerKind.NEW, now);
		boardEvents.changed("ORDER_ADDED", id, null, now);
		return id;
	}

	@Override
	@Transactional
	public void addLines(BoardSource source, UUID sourceId, List<BoardLineIntake> lines) {
		var now = Instant.now(clock);
		var order = requireOrder(source, sourceId);
		var locked = store.lock(order.id(), now, WAITING, PREPARING, READY)
			.orElseThrow(() -> store.rejection(order.id(), status -> PreparationError.ORDER_CLOSED));
		var position = store.nextPosition(order.id());
		var added = 0;
		for (var line : lines) {
			var lineId = store.insertLine(order.id(), position, line, now);
			if (lineId.isPresent()) {
				position++;
				added++;
				store.insertMarker(order.id(), lineId.get(), MarkerKind.MODIFIED, now);
			}
		}
		if (added == 0) {
			return;
		}
		if (locked.status() == READY) {
			jdbc.sql("""
					UPDATE preparation.board_order SET status = 'PREPARING', ready_at = NULL, updated_at = :now
					WHERE id = :id AND status = 'READY'
					""").param("now", utc(now)).param("id", order.id()).update();
			events.publishEvent(new OrderRecalled(order.id(), source, sourceId, now));
		}
		boardEvents.changed("LINES_ADDED", order.id(), null, now);
	}

	@Override
	@Transactional
	public void voidLine(BoardSource source, UUID sourceId, UUID sourceLineId, VoidMode mode) {
		var now = Instant.now(clock);
		var order = requireOrder(source, sourceId);
		var locked = store.lock(order.id(), now, WAITING, PREPARING, READY)
			.orElseThrow(() -> store.rejection(order.id(), status -> PreparationError.ORDER_CLOSED));
		var line = jdbc.sql("""
				SELECT id, state FROM preparation.board_line WHERE board_order_id = :order AND source_line_id = :sourceLine
				""")
			.param("order", order.id())
			.param("sourceLine", sourceLineId)
			.query((rs, row) -> new LineRef(rs.getObject("id", UUID.class), rs.getString("state")))
			.optional()
			.orElseThrow(() -> new BusinessException(PreparationError.LINE_NOT_FOUND, "The board line does not exist"));
		if ("VOIDED".equals(line.state())) {
			return;
		}
		var voided = jdbc.sql("""
				UPDATE preparation.board_line
				SET state = 'VOIDED', waste = (state = 'READY'), version = version + 1, updated_at = :now,
					resolution = CASE WHEN flagged_unavailable THEN 'VOIDED' END
				WHERE id = :id AND (state = 'PENDING' OR (:allowReady AND state = 'READY'))
				""")
			.param("now", utc(now))
			.param("id", line.id())
			.param("allowReady", mode == VoidMode.ALLOW_READY_AS_WASTE)
			.update();
		if (voided == 0) {
			var current = jdbc.sql("SELECT state FROM preparation.board_line WHERE id = :id")
				.param("id", line.id())
				.query(String.class)
				.single();
			var error = "READY".equals(current) ? PreparationError.LINE_ALREADY_READY : PreparationError.LINE_NOT_PENDING;
			throw new BusinessException(error, "Board line " + line.id() + " is " + current);
		}
		store.insertMarker(order.id(), line.id(), MarkerKind.VOID, now);
		boardEvents.changed("LINE_VOIDED", order.id(), line.id(), now);
		settle(order.id(), source, sourceId, locked.status(), now);
	}

	@Override
	@Transactional
	public void cancel(BoardSource source, UUID sourceId) {
		var now = Instant.now(clock);
		var cancelled = jdbc.sql("""
				UPDATE preparation.board_order
				SET status = 'CANCELLED', cancelled_at = :now, version = version + 1, updated_at = :now
				WHERE source = :source AND source_id = :sourceId AND status = 'WAITING'
				RETURNING id
				""")
			.param("now", utc(now))
			.param("source", source.name())
			.param("sourceId", sourceId)
			.query(UUID.class)
			.optional();
		if (cancelled.isPresent()) {
			boardEvents.changed("ORDER_CANCELLED", cancelled.get(), null, now);
			return;
		}
		var current = requireOrder(source, sourceId);
		if (current.status() != BoardOrderStatus.CANCELLED) {
			throw new BusinessException(PreparationError.ORDER_ALREADY_STARTED,
					"Board order " + current.id() + " is " + current.status() + "; cancelling requires WAITING");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BoardOrderStatus> statusOf(BoardSource source, UUID sourceId) {
		return store.find(source, sourceId).map(BoardStore.OrderRef::status);
	}

	/**
	 * Re-evaluates a card after a void, with its row lock held: nothing left to prepare cancels it, nothing left
	 * pending finishes it.
	 */
	private void settle(UUID orderId, BoardSource source, UUID sourceId, BoardOrderStatus before, Instant now) {
		var cancelled = jdbc.sql("""
				UPDATE preparation.board_order SET status = 'CANCELLED', cancelled_at = :now, updated_at = :now
				WHERE id = :id AND status IN ('WAITING', 'PREPARING', 'READY')
					AND NOT EXISTS (SELECT 1 FROM preparation.board_line
						WHERE board_order_id = :id AND state IN ('PENDING', 'READY'))
				""").param("now", utc(now)).param("id", orderId).update();
		if (cancelled == 1) {
			boardEvents.changed("ORDER_CANCELLED", orderId, null, now);
			return;
		}
		if (before != PREPARING) {
			return;
		}
		var ready = jdbc.sql("""
				UPDATE preparation.board_order SET status = 'READY', ready_at = :now, updated_at = :now
				WHERE id = :id AND status = 'PREPARING'
					AND NOT EXISTS (SELECT 1 FROM preparation.board_line WHERE board_order_id = :id AND state = 'PENDING')
				""").param("now", utc(now)).param("id", orderId).update();
		if (ready == 1) {
			events.publishEvent(new OrderReady(orderId, source, sourceId, now));
			boardEvents.changed("ORDER_READY", orderId, null, now);
		}
	}

	private BoardStore.OrderRef requireOrder(BoardSource source, UUID sourceId) {
		return store.find(source, sourceId)
			.orElseThrow(() -> new BusinessException(PreparationError.ORDER_NOT_FOUND,
					"The " + source + " has no card on the board"));
	}

	private record LineRef(UUID id, String state) {
	}

}
