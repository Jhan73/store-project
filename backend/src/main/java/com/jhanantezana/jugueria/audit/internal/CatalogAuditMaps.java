package com.jhanantezana.jugueria.audit.internal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.ModifierGroupSnapshot;
import com.jhanantezana.jugueria.catalog.ModifierOptionSnapshot;
import com.jhanantezana.jugueria.catalog.ProductSnapshot;

// Snapshots become plain maps here so the audit row does not depend on the catalog's record shapes over time.
final class CatalogAuditMaps {

	private CatalogAuditMaps() {
	}

	static Map<String, Object> toMap(ModifierGroupSnapshot snapshot) {
		var map = new LinkedHashMap<String, Object>();
		map.put("name", snapshot.name());
		map.put("required", snapshot.required());
		map.put("minChoices", snapshot.minChoices());
		map.put("maxChoices", snapshot.maxChoices());
		map.put("options", snapshot.options().stream().map(CatalogAuditMaps::toMap).toList());
		return map;
	}

	static Map<String, Object> toMap(ModifierOptionSnapshot option) {
		var map = new LinkedHashMap<String, Object>();
		map.put("id", option.id().toString());
		map.put("name", option.name());
		map.put("priceDelta", option.priceDelta().amount().toPlainString());
		map.put("available", option.available());
		map.put("allergens", names(option.allergens()));
		return map;
	}

	static Map<String, Object> toMap(ProductSnapshot snapshot) {
		var map = new LinkedHashMap<String, Object>();
		map.put("name", snapshot.name());
		map.put("description", snapshot.description());
		map.put("categoryId", snapshot.categoryId().toString());
		map.put("price", snapshot.price().amount().toPlainString());
		map.put("displayOrder", snapshot.displayOrder());
		map.put("quickSalePinned", snapshot.quickSalePinned());
		map.put("active", snapshot.active());
		map.put("imageKey", snapshot.imageKey());
		map.put("allergens", names(snapshot.allergens()));
		map.put("modifierGroupIds", snapshot.modifierGroupIds().stream().map(UUID::toString).toList());
		return map;
	}

	static List<String> names(Set<Allergen> allergens) {
		return allergens.stream().sorted(Comparator.naturalOrder()).map(Allergen::name).toList();
	}

}
