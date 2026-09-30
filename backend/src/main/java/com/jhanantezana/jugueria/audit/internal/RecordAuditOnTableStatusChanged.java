package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TableStatusChanged;

@Component
class RecordAuditOnTableStatusChanged {

	private final AuditLogService auditLog;

	RecordAuditOnTableStatusChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TableStatusChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"TABLE_STATUS_CHANGED", TableAuditMaps.ENTITY_TYPE, event.tableId(), null,
				Map.of("active", event.active()), null));
	}

}
