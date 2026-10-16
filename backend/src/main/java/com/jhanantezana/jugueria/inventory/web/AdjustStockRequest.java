package com.jhanantezana.jugueria.inventory.web;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code quantityDelta} is signed and never zero; {@code reason} is the code of a stock adjustment reason. */
record AdjustStockRequest(@Min(-StockLimits.MAX_QUANTITY) @Max(StockLimits.MAX_QUANTITY) int quantityDelta,
		@NotBlank @Size(max = 60) String reason, @Nullable @Size(max = StockLimits.MAX_NOTE_LENGTH) String note) {
}
