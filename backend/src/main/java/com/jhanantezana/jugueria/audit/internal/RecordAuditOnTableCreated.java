package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TableCreated;

@Component
class RecordAuditOnTableCreated {

	private final AuditLogService auditLog;

	RecordAuditOnTableCreated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TableCreated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TABLE_CREATED",
				TableAuditMaps.ENTITY_TYPE, event.tableId(), null, TableAuditMaps.of(event.after()), null));
	}

}
