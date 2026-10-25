package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftClosed;

@Component
class RecordAuditOnShiftClosed {

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnShiftClosed(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(ShiftClosed event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("status", "CLOSED");
		after.put("kind", event.forced() ? "FORCED" : "COUNTED");
		after.put("openingFloat", event.openingFloat().amount().toPlainString());
		after.put("countedCash", event.countedCash() == null ? null : event.countedCash().amount().toPlainString());
		after.put("expectedCash", event.expectedCash().amount().toPlainString());
		after.put("difference", event.difference() == null ? null : event.difference().amount().toPlainString());
		var reason = event.forced() ? event.forceReason() : event.differenceNote();
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "SHIFT_CLOSED",
				RecordAuditOnShiftOpened.ENTITY_TYPE, event.shiftId(), Map.of("status", "OPEN"), after, reason,
				labels.user(event.cashierId())));
	}

}
