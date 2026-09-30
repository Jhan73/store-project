package com.jhanantezana.jugueria.catalog.web;

import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.internal.ModifierOptionDefinition;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// A missing id creates a new option; an id from the group keeps that option.
record SaveModifierOptionRequest(@Nullable UUID id, @NotBlank @Size(max = 120) String name,
		@NotNull Money priceDelta, @Nullable Set<Allergen> allergens) {

	ModifierOptionDefinition toDefinition() {
		return new ModifierOptionDefinition(id, name, priceDelta, allergens == null ? Set.of() : allergens);
	}

}
