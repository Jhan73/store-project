package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.DeliveryZoneSnapshot;
import com.jhanantezana.jugueria.store.DeliveryZoneUpdated;

@Component
class RecordAuditOnDeliveryZoneUpdated {

	private static final String ENTITY_TYPE = "DELIVERY_ZONE";

	private final AuditLogService auditLog;

	RecordAuditOnDeliveryZoneUpdated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(DeliveryZoneUpdated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"DELIVERY_ZONE_UPDATED", ENTITY_TYPE, event.zoneId(), toMap(event.before()), toMap(event.after()),
				null));
	}

	private static Map<String, Object> toMap(DeliveryZoneSnapshot snapshot) {
		var map = new java.util.LinkedHashMap<String, Object>();
		map.put("name", snapshot.name());
		map.put("fee", snapshot.fee().amount().toPlainString());
		map.put("deliveryMinutes", snapshot.deliveryMinutes());
		map.put("minimumOrder", amountOrNull(snapshot.minimumOrder()));
		map.put("freeDeliveryThreshold", amountOrNull(snapshot.freeDeliveryThreshold()));
		return map;
	}

	private static @Nullable String amountOrNull(@Nullable Money money) {
		return money == null ? null : money.amount().toPlainString();
	}

}
