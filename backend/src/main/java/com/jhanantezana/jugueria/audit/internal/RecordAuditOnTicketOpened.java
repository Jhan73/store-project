package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketOpened;

@Component
class RecordAuditOnTicketOpened {

	static final String ENTITY_TYPE = "TICKET";

	private final AuditLogService auditLog;

	RecordAuditOnTicketOpened(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TicketOpened event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("number", Long.toString(event.number()));
		after.put("type", event.type().name());
		after.put("tableId", event.tableId() == null ? null : event.tableId().toString());
		after.put("counterName", event.counterName());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TICKET_OPENED",
				ENTITY_TYPE, event.ticketId(), null, after, null,
				AuditLabels.ticketNumber(event.number())));
	}

}
