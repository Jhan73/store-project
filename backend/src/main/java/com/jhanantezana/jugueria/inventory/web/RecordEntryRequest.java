package com.jhanantezana.jugueria.inventory.web;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

record RecordEntryRequest(@Min(1) @Max(StockLimits.MAX_QUANTITY) int quantity,
		@Nullable @Size(max = StockLimits.MAX_NOTE_LENGTH) String note) {
}
