package com.jhanantezana.jugueria.preparation.internal;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.StationDeactivationGuard;
import com.jhanantezana.jugueria.shared.BusinessException;

/** A station with cards still on the board cannot be retired: its kitchen would stop seeing them. */
@Component
class LiveStationCardsGuard implements StationDeactivationGuard {

	private final JdbcClient jdbc;

	LiveStationCardsGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void requireCanDeactivate(UUID stationId) {
		var live = jdbc.sql("""
				SELECT EXISTS (
					SELECT 1 FROM preparation.board_line l JOIN preparation.board_order o ON o.id = l.board_order_id
					WHERE l.station_id = :station AND l.state IN ('PENDING', 'READY')
						AND o.status IN ('WAITING', 'PREPARING', 'READY'))
				""").param("station", stationId).query(Boolean.class).single();
		if (live) {
			throw new BusinessException(CatalogError.STATION_HAS_LIVE_ORDERS,
					"Cards routed to the station are still on the board");
		}
	}

}
