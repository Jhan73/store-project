package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jhanantezana.jugueria.instore.TableSnapshot;

final class TableAuditMaps {

	static final String ENTITY_TYPE = "TABLE";

	private TableAuditMaps() {
	}

	static Map<String, Object> of(TableSnapshot snapshot) {
		var map = new LinkedHashMap<String, Object>();
		map.put("name", snapshot.name());
		map.put("area", snapshot.area());
		map.put("displayOrder", snapshot.displayOrder());
		return map;
	}

}
