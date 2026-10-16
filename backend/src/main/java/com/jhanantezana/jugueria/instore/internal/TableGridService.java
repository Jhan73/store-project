package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.TableStatus;

@Service
public class TableGridService {

	private record OpenTicket(UUID id, Instant openedAt, boolean billRequested) {
	}

	private final DiningTableRepository tables;

	private final JdbcClient jdbc;

	TableGridService(DiningTableRepository tables, JdbcClient jdbc) {
		this.tables = tables;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<TableGridEntry> grid() {
		var open = new HashMap<UUID, OpenTicket>();
		jdbc.sql("""
				SELECT table_id, id, created_at, bill_requested FROM instore.ticket
				WHERE status = 'OPEN' AND table_id IS NOT NULL
				""").query(rs -> {
			open.put(rs.getObject("table_id", UUID.class), new OpenTicket(rs.getObject("id", UUID.class),
					rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getBoolean("bill_requested")));
		});
		return tables.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream().map(table -> {
			var ticket = open.get(table.getId());
			if (ticket == null) {
				return new TableGridEntry(table, TableStatus.FREE, null, null);
			}
			var status = ticket.billRequested() ? TableStatus.WAITING_FOR_PAYMENT : TableStatus.OCCUPIED;
			return new TableGridEntry(table, status, ticket.openedAt(), ticket.id());
		}).toList();
	}

}
