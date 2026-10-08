package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.StationChanged;

@Component
class RecordAuditOnStationChanged {

	private static final String ENTITY_TYPE = "STATION";

	private final AuditLogService auditLog;

	RecordAuditOnStationChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(StationChanged event) {
		var before = event.before() == null ? null : CatalogAuditMaps.toMap(event.before());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				event.before() == null ? "STATION_CREATED" : "STATION_UPDATED", ENTITY_TYPE, event.stationId(), before,
				CatalogAuditMaps.toMap(event.after()), null));
	}

}
