package com.jhanantezana.jugueria.catalog.internal;

// The ETag is a hash of the menu's own JSON, so every task derives the same one for the same menu.
public record MenuSnapshot(MenuView menu, String etag) {
}
