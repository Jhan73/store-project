package com.jhanantezana.jugueria.catalog;

import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

/** A product the admin pinned for quick sales; its modifiers are chosen from the menu as for any other line. */
public record QuickSaleItem(UUID productId, String name, Money price) {
}
