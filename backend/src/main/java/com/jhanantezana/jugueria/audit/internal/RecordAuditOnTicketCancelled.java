package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketCancelled;

@Component
class RecordAuditOnTicketCancelled {

	private final AuditLogService auditLog;

	RecordAuditOnTicketCancelled(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TicketCancelled event) {
		var before = new LinkedHashMap<String, Object>();
		before.put("number", Long.toString(event.number()));
		before.put("type", event.type().name());
		before.put("tableId", event.tableId() == null ? null : event.tableId().toString());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TICKET_CANCELLED",
				RecordAuditOnTicketOpened.ENTITY_TYPE, event.ticketId(), before, Map.of("status", "CANCELLED"), null));
	}

}
