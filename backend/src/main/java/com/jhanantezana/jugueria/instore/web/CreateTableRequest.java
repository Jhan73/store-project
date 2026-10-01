package com.jhanantezana.jugueria.instore.web;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// A missing area means the table belongs to none; a missing display order means 0.
record CreateTableRequest(@NotBlank @Size(max = 60) String name, @Nullable @Size(max = 60) String area,
		@Nullable @PositiveOrZero @Schema(defaultValue = "0") Integer displayOrder) {

	int displayOrderOrDefault() {
		return displayOrder == null ? 0 : displayOrder;
	}

}
