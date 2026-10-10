package com.jhanantezana.jugueria.inventory;

public record StockSnapshot(boolean tracked, int onHand, int reserved, int lowStockThreshold) {
}
