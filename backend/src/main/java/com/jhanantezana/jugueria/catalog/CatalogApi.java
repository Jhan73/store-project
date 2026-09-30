package com.jhanantezana.jugueria.catalog;

import java.util.List;
import java.util.UUID;

public interface CatalogApi {

	/**
	 * Validates a product with its chosen modifier options and prices it. Always reads the database, never the
	 * menu cache. Throws {@code BusinessException} with {@code catalog.product-not-found},
	 * {@code catalog.product-unavailable}, {@code catalog.modifier-option-unavailable}, or
	 * {@code catalog.invalid-modifier-selection} (an option outside the product's groups, a repeated option, or a
	 * group below its minimum or above its maximum).
	 */
	PricedSelection priceSelection(UUID productId, List<UUID> optionIds);

}
