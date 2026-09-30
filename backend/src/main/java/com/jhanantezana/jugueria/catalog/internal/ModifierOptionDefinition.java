package com.jhanantezana.jugueria.catalog.internal;

import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.shared.Money;

// A null id asks for a new option; an existing id keeps that option's identity (and its availability).
public record ModifierOptionDefinition(@Nullable UUID id, String name, Money priceDelta, Set<Allergen> allergens) {
}
