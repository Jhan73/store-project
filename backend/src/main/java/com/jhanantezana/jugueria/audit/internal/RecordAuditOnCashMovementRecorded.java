package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.CashMovementRecorded;

@Component
class RecordAuditOnCashMovementRecorded {

	private static final String ENTITY_TYPE = "CASH_MOVEMENT";

	private final AuditLogService auditLog;

	RecordAuditOnCashMovementRecorded(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(CashMovementRecorded event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("shiftId", event.shiftId().toString());
		after.put("type", event.type().name());
		after.put("amount", event.amount().amount().toPlainString());
		after.put("reference", event.reference());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "CASH_MOVEMENT_RECORDED",
				ENTITY_TYPE, event.movementId(), null, after, reason(event.reasonCode(), event.note()),
				event.type().name() + " " + event.amount().amount().toPlainString()));
	}

	private static @Nullable String reason(@Nullable String code, @Nullable String note) {
		if (code == null) {
			return note;
		}
		return note == null ? code : code + ": " + note;
	}

}
