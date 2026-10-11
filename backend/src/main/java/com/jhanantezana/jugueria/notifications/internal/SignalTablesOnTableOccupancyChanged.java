package com.jhanantezana.jugueria.notifications.internal;

import java.util.LinkedHashMap;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalTablesOnTableOccupancyChanged {

	private final AppEventsPublisher appEvents;

	SignalTablesOnTableOccupancyChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(TableOccupancyChanged event) {
		var ids = new LinkedHashMap<String, String>();
		ids.put("tableId", event.tableId().toString());
		appEvents.publish(RealtimeTopic.TABLES, "TABLE_OCCUPANCY_CHANGED", ids);
	}

}
