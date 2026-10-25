package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.DeliveryZoneDeleted;

@Component
class RecordAuditOnDeliveryZoneDeleted {

	private static final String ENTITY_TYPE = "DELIVERY_ZONE";

	private final AuditLogService auditLog;

	RecordAuditOnDeliveryZoneDeleted(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(DeliveryZoneDeleted event) {
		auditLog.record(AuditEntry.deletion(event.occurredAt(), event.actorId(), event.actorRole(), ENTITY_TYPE,
				event.zoneId(), RecordAuditOnDeliveryZoneUpdated.toMap(event.before()), event.before().name(), null));
	}

}
