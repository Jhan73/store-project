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
		if (event.after() == null && event.before() != null) {
			auditLog.record(AuditEntry.deletion(event.occurredAt(), event.actorId(), event.actorRole(), ENTITY_TYPE,
					event.groupId(), CatalogAuditMaps.toMap(event.before()), event.before().name(), null));
			return;
		}
		var before = event.before() == null ? null : CatalogAuditMaps.toMap(event.before());
		var after = event.after() == null ? null : CatalogAuditMaps.toMap(event.after());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), action(event),
				ENTITY_TYPE, event.groupId(), before, after, null, event.after() == null ? null : event.after().name()));
	}

	private static String action(ModifierGroupChanged event) {
		return event.before() == null ? "MODIFIER_GROUP_CREATED" : "MODIFIER_GROUP_UPDATED";
	}

}
