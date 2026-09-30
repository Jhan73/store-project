package com.jhanantezana.jugueria.audit.internal;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.OpeningHourSnapshot;
import com.jhanantezana.jugueria.store.OpeningHoursChanged;

@Component
class RecordAuditOnOpeningHoursChanged {

	private static final String ENTITY_TYPE = "OPENING_HOURS";

	private final AuditLogService auditLog;

	RecordAuditOnOpeningHoursChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(OpeningHoursChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"OPENING_HOURS_CHANGED", ENTITY_TYPE, event.settingsId(), toMap(event.before()), toMap(event.after()),
				null));
	}

	private static Map<String, Object> toMap(List<OpeningHourSnapshot> week) {
		return week.stream()
			.collect(Collectors.toMap(day -> day.dayOfWeek().name(), RecordAuditOnOpeningHoursChanged::describe));
	}

	private static String describe(OpeningHourSnapshot day) {
		return day.closed() ? "CLOSED" : day.opensAt() + "-" + day.closesAt();
	}

}
