package com.jhanantezana.jugueria.catalog.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.shared.Money;

public record ProductDefinition(String name, @Nullable String description, UUID categoryId, Money price,
		int displayOrder, boolean quickSalePinned, Set<Allergen> allergens, List<UUID> modifierGroupIds) {
}
