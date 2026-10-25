package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.StationDeactivationGuard;
import com.jhanantezana.jugueria.shared.BusinessException;

/** A line keeps the station it was priced for, so an unsent one would still be sent there after it was retired. */
@Component
// Before the board guard: a send commits the line as SENT and its card together, so this check passes only once the
// card is already visible to the board check that follows.
@Order(1)
class UnsentStationLinesGuard implements StationDeactivationGuard {

	private final JdbcClient jdbc;

	UnsentStationLinesGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void requireCanDeactivate(UUID stationId) {
		var unsent = jdbc.sql("""
				SELECT EXISTS (
					SELECT 1 FROM instore.ticket_line l JOIN instore.ticket t ON t.id = l.ticket_id
					WHERE l.station_id = :station AND l.state = 'UNSENT' AND t.status = 'OPEN')
				""").param("station", stationId).query(Boolean.class).single();
		if (unsent) {
			throw new BusinessException(CatalogError.STATION_HAS_UNSENT_LINES,
					"Unsent ticket lines are still priced for the station");
		}
	}

}
