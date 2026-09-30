package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// A full replacement: an omitted area clears it.
record UpdateTableRequest(@NotBlank @Size(max = 60) String name, @Nullable @Size(max = 60) String area,
		@NotNull @PositiveOrZero Integer displayOrder) {
}
