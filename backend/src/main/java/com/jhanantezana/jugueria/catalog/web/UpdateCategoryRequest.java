package com.jhanantezana.jugueria.catalog.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record UpdateCategoryRequest(@NotBlank @Size(max = 120) String name, @NotNull UUID stationId,
		@PositiveOrZero int displayOrder) {
}
