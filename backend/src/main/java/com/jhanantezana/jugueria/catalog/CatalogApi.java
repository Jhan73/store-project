package com.jhanantezana.jugueria.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	boolean productExists(UUID productId);

	/** The product's current name, empty for an unknown product. */
	java.util.Optional<String> productName(UUID productId);

	/** The given products that cannot be sold right now: unknown, inactive, unavailable or in a hidden category. */
	Set<UUID> notOnSale(Collection<UUID> productIds);

	/** Whether the admin pinned the product as a frequent item, on sale or not; false for an unknown product. */
	boolean isQuickSalePinned(UUID productId);

	/** The products pinned for quick sales that can be sold right now, in the order the admin set. */
	List<QuickSaleItem> quickSaleItems();

	/** The current category of each product that exists; unknown IDs are left out of the result. */
	Map<UUID, ProductCategory> categoriesOf(Collection<UUID> productIds);

	/**
	 * Sets the product's availability and publishes {@link AvailabilityChanged} when the flag flips, in the
	 * caller's transaction. Used by {@code inventory} so a stock-tracked product is unavailable exactly while
	 * nothing is left to sell. Throws {@code BusinessException} with {@code catalog.product-not-found}.
	 */
	void setProductAvailability(UUID productId, boolean available);

}
