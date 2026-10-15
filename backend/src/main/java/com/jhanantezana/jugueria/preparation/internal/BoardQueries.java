package com.jhanantezana.jugueria.preparation.internal;

import static com.jhanantezana.jugueria.preparation.internal.BoardStore.utc;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.PreparationError;
import com.jhanantezana.jugueria.shared.BusinessException;

/** What the board screens read. A card scheduled for later stays out of every view until its fire time. */
@Service
public class BoardQueries {

	private static final String VISIBLE = "(o.fire_at IS NULL OR o.fire_at <= :now)";

	private static final String STATION = " AND EXISTS (SELECT 1 FROM preparation.board_line l "
			+ "WHERE l.board_order_id = o.id AND l.station_id = :station AND l.state IN ('PENDING', 'READY'))";

	private final BoardStore store;

	private final JdbcClient jdbc;

	private final Clock clock;

	BoardQueries(BoardStore store, JdbcClient jdbc, Clock clock) {
		this.store = store;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Cards to prepare, oldest first: waiting and being prepared. */
	@Transactional(readOnly = true)
	public List<BoardOrderView> tickets(@Nullable UUID station) {
		return store.cards("o.status IN ('WAITING', 'PREPARING') AND " + VISIBLE + stationFilter(station),
				params(station), "coalesce(o.fire_at, o.sent_at), o.sent_at, o.id", station);
	}

	/** Cards waiting for handover, the ones that have waited longest first. */
	@Transactional(readOnly = true)
	public List<BoardOrderView> ready(@Nullable UUID station) {
		return store.cards("o.status = 'READY'" + stationFilter(station), params(station), "o.ready_at, o.id", station);
	}

	/** Identical pending items counted together; lines with a note or an allergy are listed one by one. */
	@Transactional(readOnly = true)
	public AllDayView allDay(@Nullable UUID station) {
		var filter = """
				FROM preparation.board_line l JOIN preparation.board_order o ON o.id = l.board_order_id
				WHERE o.status IN ('WAITING', 'PREPARING') AND %s AND l.state = 'PENDING'
				""".formatted(VISIBLE) + (station == null ? "" : " AND l.station_id = :station ");
		var groups = query("""
				SELECT l.product_id, l.display_name, l.modifiers_text, sum(l.quantity)::integer AS quantity
				""" + filter + """
				AND l.note IS NULL AND NOT l.allergy
				GROUP BY l.product_id, l.modifier_signature, l.display_name, l.modifiers_text
				ORDER BY sum(l.quantity) DESC, l.display_name, l.product_id, l.modifier_signature
				""", station).query((rs, row) -> new AllDayView.Group(rs.getObject("product_id", UUID.class),
				rs.getString("display_name"), rs.getString("modifiers_text"), rs.getInt("quantity"))).list();
		var individual = query("""
				SELECT l.id, l.board_order_id, o.short_number, l.product_id, l.display_name, l.quantity, l.modifiers_text,
					l.note, l.allergy
				""" + filter + """
				AND (l.note IS NOT NULL OR l.allergy)
				ORDER BY coalesce(o.fire_at, o.sent_at), o.id, l.position
				""", station).query((rs, row) -> new AllDayView.Individual(rs.getObject("id", UUID.class),
				rs.getObject("board_order_id", UUID.class), rs.getString("short_number"),
				rs.getObject("product_id", UUID.class), rs.getString("display_name"), rs.getInt("quantity"),
				rs.getString("modifiers_text"), rs.getString("note"), rs.getBoolean("allergy"))).list();
		return new AllDayView(groups, individual);
	}

	/**
	 * What the customer screen shows: numbers and first names only. Waiting and preparing cards share a column, since a
	 * customer cannot tell them apart.
	 */
	@Transactional(readOnly = true)
	public DisplayView display() {
		var preparing = new ArrayList<DisplayView.Entry>();
		var ready = new ArrayList<DisplayView.Entry>();
		jdbc.sql("""
				SELECT o.short_number, o.channel, o.label, o.status FROM preparation.board_order o
				WHERE o.status IN ('WAITING', 'PREPARING', 'READY') AND %s
				ORDER BY CASE WHEN o.status = 'READY' THEN o.ready_at ELSE coalesce(o.fire_at, o.sent_at) END, o.sent_at, o.id
				""".formatted(VISIBLE)).param("now", utc(Instant.now(clock))).query(rs -> {
			var channel = BoardChannel.valueOf(rs.getString("channel"));
			var entry = new DisplayView.Entry(rs.getString("short_number"), FirstName.of(channel, rs.getString("label")));
			("READY".equals(rs.getString("status")) ? ready : preparing).add(entry);
		});
		return new DisplayView(preparing, ready);
	}

	/** The card in whatever status it is in; the commands return it to the caller. */
	@Transactional(readOnly = true)
	public BoardOrderView card(UUID id) {
		return store.cards("o.id = :id", Map.of("id", id), "o.id", null)
			.stream()
			.findFirst()
			.orElseThrow(() -> new BusinessException(PreparationError.ORDER_NOT_FOUND, "The board order does not exist"));
	}

	@Transactional(readOnly = true)
	public MarkerView marker(UUID id) {
		return store.marker(id)
			.orElseThrow(() -> new BusinessException(PreparationError.MARKER_NOT_FOUND, "The marker does not exist"));
	}

	private JdbcClient.StatementSpec query(String sql, @Nullable UUID station) {
		var spec = jdbc.sql(sql).param("now", utc(Instant.now(clock)));
		return station == null ? spec : spec.param("station", station);
	}

	private Map<String, Object> params(@Nullable UUID station) {
		var params = new LinkedHashMap<String, Object>();
		params.put("now", utc(Instant.now(clock)));
		if (station != null) {
			params.put("station", station);
		}
		return params;
	}

	private static String stationFilter(@Nullable UUID station) {
		return station == null ? "" : STATION;
	}

}
