package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.StoreSettingsChanged;
import com.jhanantezana.jugueria.store.StoreSettingsSnapshot;

@Component
class RecordAuditOnStoreSettingsChanged {

	private static final String ENTITY_TYPE = "STORE_SETTINGS";

	private final AuditLogService auditLog;

	RecordAuditOnStoreSettingsChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(StoreSettingsChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"STORE_SETTINGS_CHANGED", ENTITY_TYPE, event.settingsId(), toMap(event.before()),
				toMap(event.after()), null));
	}

	private static Map<String, Object> toMap(StoreSettingsSnapshot snapshot) {
		var map = new LinkedHashMap<String, Object>();
		map.put("timeZone", snapshot.timeZone());
		map.put("currency", snapshot.currency().getCurrencyCode());
		map.put("basePrepMinutes", snapshot.basePrepMinutes());
		map.put("queueMinutesPerOrder", snapshot.queueMinutesPerOrder());
		map.put("busyModeMinutes", snapshot.busyModeMinutes());
		map.put("boardWarningMinutes", snapshot.boardWarningMinutes());
		map.put("boardLateMinutes", snapshot.boardLateMinutes());
		map.put("registerDifferenceThreshold", snapshot.registerDifferenceThreshold().amount().toPlainString());
		map.put("exceptionThreshold", snapshot.exceptionThreshold());
		map.put("onlineCapacityLimit", snapshot.onlineCapacityLimit());
		return map;
	}

}
