package com.jhanantezana.jugueria.catalog;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

public record ProductSnapshot(String name, @Nullable String description, UUID categoryId, Money price,
		int displayOrder, boolean quickSalePinned, boolean active, @Nullable String imageKey,
		Set<Allergen> allergens, List<UUID> modifierGroupIds) {
}
