package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftCountRevealed;

@Component
class RecordAuditOnShiftCountRevealed {

	private final AuditLogService auditLog;

	RecordAuditOnShiftCountRevealed(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(ShiftCountRevealed event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("countedCash", event.countedCash().amount().toPlainString());
		after.put("difference", event.difference().amount().toPlainString());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "SHIFT_COUNT_REVEALED",
				RecordAuditOnShiftOpened.ENTITY_TYPE, event.shiftId(), null, after, null));
	}

}
