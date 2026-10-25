package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.DeliveryZoneStatusChanged;

@Component
class RecordAuditOnDeliveryZoneStatusChanged {

	private static final String ENTITY_TYPE = "DELIVERY_ZONE";

	private final AuditLogService auditLog;

	RecordAuditOnDeliveryZoneStatusChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(DeliveryZoneStatusChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"DELIVERY_ZONE_STATUS_CHANGED", ENTITY_TYPE, event.zoneId(),
				AuditSnapshots.status("name", event.name(), !event.active()),
				AuditSnapshots.status("name", event.name(), event.active()), null, event.name()));
	}

}
