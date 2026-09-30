package com.jhanantezana.jugueria.catalog.internal;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.shared.Money;

// Built inside the service's transaction so the web layer never touches a lazy collection.
public record ProductDetails(UUID id, String name, @Nullable String description, UUID categoryId, Money price,
		int displayOrder, boolean quickSalePinned, boolean active, boolean available, @Nullable String imageKey,
		List<Allergen> allergens, List<UUID> modifierGroupIds, long version) {

	static ProductDetails from(Product product) {
		return new ProductDetails(product.getId(), product.getName(), product.getDescription(),
				product.getCategoryId(), product.getPrice(), product.getDisplayOrder(), product.isQuickSalePinned(),
				product.isActive(), product.isAvailable(), product.getImageKey(),
				product.getAllergens().stream().sorted(Comparator.naturalOrder()).toList(),
				List.copyOf(product.getModifierGroupIds()), product.getVersion());
	}

}
