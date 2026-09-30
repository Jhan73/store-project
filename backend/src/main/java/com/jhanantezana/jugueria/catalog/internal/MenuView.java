package com.jhanantezana.jugueria.catalog.internal;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.shared.Money;

// What both channels browse: active categories and products only, with everything a customer needs before adding
// to the cart. Field order is the serialized order, which the menu's ETag depends on.
public record MenuView(List<Category> categories) {

	public record Category(UUID id, String name, List<Product> products) {
	}

	public record Product(UUID id, String name, @Nullable String description, Money price, @Nullable String imageKey,
			boolean available, List<Allergen> allergens, List<Group> modifierGroups) {
	}

	public record Group(UUID id, String name, boolean required, int minChoices, int maxChoices,
			List<Option> options) {
	}

	public record Option(UUID id, String name, Money priceDelta, boolean available, List<Allergen> allergens) {
	}

}
