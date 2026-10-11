package com.jhanantezana.testsupport;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardSource;

public final class PreparationTables {

	private PreparationTables() {
	}

	public static void clean(JdbcClient jdbc) {
		jdbc.sql("delete from preparation.board_marker").update();
		jdbc.sql("delete from preparation.board_line").update();
		jdbc.sql("delete from preparation.board_order").update();
	}

	public static BoardLineIntake aLine() {
		return aLine(UUID.randomUUID());
	}

	public static BoardLineIntake aLine(UUID productId) {
		return new BoardLineIntake(UUID.randomUUID(), productId, "Orange juice", 1, null, List.of(), null, false, null);
	}

	public static BoardLineIntake aLine(UUID productId, String name, int quantity, List<UUID> options) {
		return new BoardLineIntake(UUID.randomUUID(), productId, name, quantity, null, options, null, false, null);
	}

	public static BoardLineIntake aLineWithNote(String note) {
		return new BoardLineIntake(UUID.randomUUID(), UUID.randomUUID(), "Orange juice", 1, null, List.of(), note, false,
				null);
	}

	public static BoardLineIntake anAllergyLine() {
		return new BoardLineIntake(UUID.randomUUID(), UUID.randomUUID(), "Orange juice", 1, null, List.of(), null, true,
				null);
	}

	public static BoardLineIntake aLineAt(UUID stationId) {
		return new BoardLineIntake(UUID.randomUUID(), UUID.randomUUID(), "Orange juice", 1, null, List.of(), null, false,
				stationId);
	}

	public static BoardOrderIntake anOrder(BoardLineIntake... lines) {
		return anOrder(UUID.randomUUID(), lines);
	}

	public static BoardOrderIntake anOrder(UUID sourceId, BoardLineIntake... lines) {
		return new BoardOrderIntake(BoardSource.TICKET, sourceId, "L-017", BoardChannel.TABLE, "Table 4", Instant.now(),
				null, lines.length == 0 ? List.of(aLine()) : List.of(lines));
	}

	public static BoardOrderIntake aScheduledOrder(Instant fireAt, BoardLineIntake... lines) {
		return new BoardOrderIntake(BoardSource.ONLINE, UUID.randomUUID(), "W-042", BoardChannel.PICKUP, "Ana",
				Instant.now(), fireAt, lines.length == 0 ? List.of(aLine()) : List.of(lines));
	}

	public static BoardOrderIntake anOrderSentAt(Instant sentAt, BoardLineIntake... lines) {
		return new BoardOrderIntake(BoardSource.TICKET, UUID.randomUUID(), "L-017", BoardChannel.TABLE, "Table 4", sentAt,
				null, lines.length == 0 ? List.of(aLine()) : List.of(lines));
	}

	public static String orderStatus(JdbcClient jdbc, UUID orderId) {
		return jdbc.sql("select status from preparation.board_order where id = ?")
			.param(orderId)
			.query(String.class)
			.single();
	}

	public static String lineState(JdbcClient jdbc, UUID lineId) {
		return jdbc.sql("select state from preparation.board_line where id = ?")
			.param(lineId)
			.query(String.class)
			.single();
	}

	public static List<UUID> lineIds(JdbcClient jdbc, UUID orderId) {
		return jdbc.sql("select id from preparation.board_line where board_order_id = ? order by created_at, id")
			.param(orderId)
			.query(UUID.class)
			.list();
	}

	public static UUID lineIdOf(JdbcClient jdbc, UUID orderId, UUID sourceLineId) {
		return jdbc.sql("select id from preparation.board_line where board_order_id = ? and source_line_id = ?")
			.param(orderId)
			.param(sourceLineId)
			.query(UUID.class)
			.single();
	}

	public static long count(JdbcClient jdbc, String table) {
		return jdbc.sql("select count(*) from preparation." + table).query(Long.class).single();
	}

	/** The kinds of the order's markers, oldest first, with "*" appended while a marker is unacknowledged. */
	public static List<String> markers(JdbcClient jdbc, UUID orderId) {
		return jdbc.sql("""
				select kind || case when acknowledged_at is null then '*' else '' end
				from preparation.board_marker where board_order_id = ? order by created_at, id
				""").param(orderId).query(String.class).list();
	}

	public static UUID markerId(JdbcClient jdbc, UUID orderId, String kind) {
		return jdbc.sql("select id from preparation.board_marker where board_order_id = ? and kind = ? "
				+ "order by created_at, id limit 1").param(orderId).param(kind).query(UUID.class).single();
	}

	/** Moves a card straight to a status, for tests about the commands that start from it. */
	public static void forceStatus(JdbcClient jdbc, UUID orderId, String status) {
		jdbc.sql("update preparation.board_order set status = ? where id = ?").param(status).param(orderId).update();
	}

	/** Backdates the moment the order became ready / was handed over. */
	public static void completedAt(JdbcClient jdbc, UUID orderId, Instant readyAt, Instant handedOverAt) {
		jdbc.sql("update preparation.board_order set ready_at = ?, handed_over_at = ? where id = ?")
			.param(readyAt == null ? null : OffsetDateTime.ofInstant(readyAt, ZoneOffset.UTC))
			.param(handedOverAt == null ? null : OffsetDateTime.ofInstant(handedOverAt, ZoneOffset.UTC))
			.param(orderId)
			.update();
	}

}
