package com.jhanantezana.jugueria.catalog;

import java.util.Set;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

public record ModifierOptionSnapshot(UUID id, String name, Money priceDelta, boolean available,
		Set<Allergen> allergens) {
}
