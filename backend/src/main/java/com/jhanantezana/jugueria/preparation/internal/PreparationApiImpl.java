package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.HANDED_OVER;
import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.PREPARING;
import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.READY;
import static com.jhanantezana.jugueria.preparation.BoardOrderStatus.WAITING;
import static com.jhanantezana.jugueria.preparation.internal.BoardStore.utc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
		add(source, sourceId, lines, true);
	}

	@Override
	@Transactional
	public boolean tryAddLines(BoardSource source, UUID sourceId, List<BoardLineIntake> lines) {
		return add(source, sourceId, lines, false);
	}

	private boolean add(BoardSource source, UUID sourceId, List<BoardLineIntake> lines, boolean failWhenClosed) {
		var now = Instant.now(clock);
		var order = failWhenClosed ? requireOrder(source, sourceId) : store.find(source, sourceId).orElse(null);
		if (order == null) {
			return false;
		}
		var sourceLines = lines.stream().map(BoardLineIntake::sourceLineId).collect(Collectors.toSet());
		if (store.allPresent(order.id(), sourceLines)) {
			return true;
		}
		var locked = store.lock(order.id(), now, WAITING, PREPARING, READY).orElse(null);
		if (locked == null) {
			// A redelivery that lost to a later status change still has nothing left to do.
			if (store.allPresent(order.id(), sourceLines)) {
				return true;
			}
			if (!failWhenClosed) {
				return false;
			}
			throw store.rejection(order.id(), status -> PreparationError.ORDER_CLOSED);
		}
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
			return true;
		}
		if (locked.status() == READY) {
			jdbc.sql("""
					UPDATE preparation.board_order SET status = 'PREPARING', ready_at = NULL, updated_at = :now
					WHERE id = :id AND status = 'READY'
					""").param("now", utc(now)).param("id", order.id()).update();
			events.publishEvent(new OrderRecalled(order.id(), source, sourceId, now));
		}
		boardEvents.changed("LINES_ADDED", order.id(), null, now);
		return true;
	}

	@Override
	@Transactional
	public boolean voidLine(BoardSource source, UUID sourceId, UUID sourceLineId, VoidMode mode) {
		var now = Instant.now(clock);
		var order = requireOrder(source, sourceId);
		var line = jdbc.sql("""
				SELECT id, state, waste FROM preparation.board_line
				WHERE board_order_id = :order AND source_line_id = :sourceLine
				""")
			.param("order", order.id())
			.param("sourceLine", sourceLineId)
			.query((rs, row) -> new LineRef(rs.getObject("id", UUID.class), rs.getString("state"), rs.getBoolean("waste")))
			.optional()
			.orElseThrow(() -> new BusinessException(PreparationError.LINE_NOT_FOUND, "The board line does not exist"));
		if ("VOIDED".equals(line.state())) {
			return line.waste();
		}
		// A handed-over line can still be rejected by the customer, so only a cancelled card is closed to voids.
		var locked = store.lock(order.id(), now, WAITING, PREPARING, READY, HANDED_OVER).orElse(null);
		if (locked == null) {
			// A retry of the void that closed the card finds it closed.
			var finished = lineRef(line.id());
			if ("VOIDED".equals(finished.state())) {
				return finished.waste();
			}
			throw store.rejection(order.id(), status -> PreparationError.ORDER_CLOSED);
		}
		var waste = jdbc.sql("""
				UPDATE preparation.board_line
				SET state = 'VOIDED', waste = (state = 'READY'), version = version + 1, updated_at = :now,
					resolution = CASE WHEN flagged_unavailable THEN 'VOIDED' END
				WHERE id = :id AND (state = 'PENDING' OR (:allowReady AND state = 'READY'))
				RETURNING waste
				""")
			.param("now", utc(now))
			.param("id", line.id())
			.param("allowReady", mode == VoidMode.ALLOW_READY_AS_WASTE)
			.query(Boolean.class)
			.optional();
		if (waste.isEmpty()) {
			var current = lineRef(line.id());
			if ("VOIDED".equals(current.state())) {
				return current.waste();
			}
			var error = "READY".equals(current.state()) ? PreparationError.LINE_ALREADY_READY
					: PreparationError.LINE_NOT_PENDING;
			throw new BusinessException(error, "Board line " + line.id() + " is " + current.state());
		}
		store.insertMarker(order.id(), line.id(), MarkerKind.VOID, now);
		boardEvents.changed("LINE_VOIDED", order.id(), line.id(), now);
		settle(order.id(), source, sourceId, locked.status(), now);
		return waste.get();
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
		// The public code is shared by every status but WAITING; renaming it would break callers.
		if (current.status() != BoardOrderStatus.CANCELLED) {
			throw new BusinessException(PreparationError.ORDER_ALREADY_STARTED,
					"Board order " + current.id() + " is " + current.status() + "; cancelling requires WAITING");
		}
	}

	@Override
	@Transactional
	public void relabel(BoardSource source, UUID sourceId, String label) {
		var now = Instant.now(clock);
		var text = label.strip();
		if (text.isEmpty() || text.length() > BoardOrderIntake.MAX_LABEL) {
			throw new IllegalArgumentException("A label needs 1 to " + BoardOrderIntake.MAX_LABEL + " characters");
		}
		var relabelled = jdbc.sql("""
				UPDATE preparation.board_order SET label = :label, version = version + 1, updated_at = :now
				WHERE source = :source AND source_id = :sourceId AND label IS DISTINCT FROM :label
				RETURNING id
				""")
			.param("label", text)
			.param("now", utc(now))
			.param("source", source.name())
			.param("sourceId", sourceId)
			.query(UUID.class)
			.optional();
		relabelled.ifPresent(id -> boardEvents.changed("ORDER_RELABELLED", id, null, now));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BoardOrderStatus> statusOf(BoardSource source, UUID sourceId) {
		return store.find(source, sourceId).map(BoardStore.OrderRef::status);
	}

	private LineRef lineRef(UUID lineId) {
		return jdbc.sql("SELECT id, state, waste FROM preparation.board_line WHERE id = :id")
			.param("id", lineId)
			.query((rs, row) -> new LineRef(rs.getObject("id", UUID.class), rs.getString("state"), rs.getBoolean("waste")))
			.single();
	}

	// With the card's row lock held: no live line cancels the card, no pending line finishes it.
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

	private record LineRef(UUID id, String state, boolean waste) {
	}

}
