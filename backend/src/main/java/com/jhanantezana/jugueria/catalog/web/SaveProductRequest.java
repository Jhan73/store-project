package com.jhanantezana.jugueria.catalog.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.internal.ProductDefinition;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record SaveProductRequest(@NotBlank @Size(max = 120) String name, @Nullable @Size(max = 500) String description,
		@NotNull UUID categoryId, @NotNull Money price, @PositiveOrZero int displayOrder, boolean quickSalePinned,
		@Nullable Set<Allergen> allergens, @Nullable List<@NotNull UUID> modifierGroupIds) {

	ProductDefinition toDefinition() {
		return new ProductDefinition(name, description, categoryId, price, displayOrder, quickSalePinned,
				allergens == null ? Set.of() : allergens, modifierGroupIds == null ? List.of() : modifierGroupIds);
	}

}
