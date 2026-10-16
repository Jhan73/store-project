package com.jhanantezana.jugueria.catalog;

import java.util.UUID;

/** The category a product is in right now. */
public record ProductCategory(UUID categoryId, String categoryName) {
}
