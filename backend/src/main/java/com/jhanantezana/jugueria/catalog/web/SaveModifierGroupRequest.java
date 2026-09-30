package com.jhanantezana.jugueria.catalog.web;

import java.util.List;

import com.jhanantezana.jugueria.catalog.internal.ModifierOptionDefinition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record SaveModifierGroupRequest(@NotBlank @Size(max = 120) String name, boolean required,
		@PositiveOrZero int minChoices, @Positive int maxChoices,
		@NotNull @Size(min = 1, max = 50) List<@Valid @NotNull SaveModifierOptionRequest> options) {

	List<ModifierOptionDefinition> optionDefinitions() {
		return options.stream().map(SaveModifierOptionRequest::toDefinition).toList();
	}

}
