package com.jhanantezana.jugueria.catalog.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// A missing station puts the category on the default one.
record CreateCategoryRequest(@NotBlank @Size(max = 120) String name, @Nullable UUID stationId,
		@PositiveOrZero int displayOrder) {
}
