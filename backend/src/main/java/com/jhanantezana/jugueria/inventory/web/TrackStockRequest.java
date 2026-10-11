package com.jhanantezana.jugueria.inventory.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

record TrackStockRequest(boolean tracked, @Min(0) @Max(StockLimits.MAX_QUANTITY) int lowStockThreshold) {
}
