package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftOpened;

@Component
class RecordAuditOnShiftOpened {

	static final String ENTITY_TYPE = "SHIFT";

	private final AuditLogService auditLog;

	RecordAuditOnShiftOpened(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(ShiftOpened event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("cashierId", event.cashierId().toString());
		after.put("openingFloat", event.openingFloat().amount().toPlainString());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "SHIFT_OPENED",
				ENTITY_TYPE, event.shiftId(), null, after, null));
	}

}
