package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;

@Component
class RecordAuditOnModifierGroupChanged {

	private static final String ENTITY_TYPE = "MODIFIER_GROUP";

	private final AuditLogService auditLog;

	RecordAuditOnModifierGroupChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(ModifierGroupChanged event) {
		var before = event.before() == null ? null : CatalogAuditMaps.toMap(event.before());
		var after = event.after() == null ? null : CatalogAuditMaps.toMap(event.after());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), action(event),
				ENTITY_TYPE, event.groupId(), before, after, null));
	}

	private static String action(ModifierGroupChanged event) {
		if (event.before() == null) {
			return "MODIFIER_GROUP_CREATED";
		}
		return event.after() == null ? "MODIFIER_GROUP_DELETED" : "MODIFIER_GROUP_UPDATED";
	}

}
