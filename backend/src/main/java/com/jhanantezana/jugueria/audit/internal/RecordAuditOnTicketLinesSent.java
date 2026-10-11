package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketLinesSent;

@Component
class RecordAuditOnTicketLinesSent {

	private final AuditLogService auditLog;

	RecordAuditOnTicketLinesSent(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TicketLinesSent event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("boardSourceId", event.boardSourceId().toString());
		after.put("lineIds", event.lineIds().stream().map(UUID::toString).toList());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TICKET_LINES_SENT",
				RecordAuditOnTicketOpened.ENTITY_TYPE, event.ticketId(), null, after, null));
	}

}
