package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

final class AuditSnapshots {

	private AuditSnapshots() {
	}

	// A status flip carries the entity's name too, so the row alone says what was switched.
	static Map<String, Object> status(String labelKey, @Nullable String label, boolean active) {
		var map = new LinkedHashMap<String, Object>();
		if (label != null) {
			map.put(labelKey, label);
		}
		map.put("active", active);
		return map;
	}

}
