package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.DeliveryZoneCreated;

@Component
class RecordAuditOnDeliveryZoneCreated {

	private static final String ENTITY_TYPE = "DELIVERY_ZONE";

	private final AuditLogService auditLog;

	RecordAuditOnDeliveryZoneCreated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(DeliveryZoneCreated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"DELIVERY_ZONE_CREATED", ENTITY_TYPE, event.zoneId(), null,
				Map.of("name", event.name(), "fee", event.fee().amount().toPlainString(), "deliveryMinutes",
						event.deliveryMinutes()),
				null));
	}

}
