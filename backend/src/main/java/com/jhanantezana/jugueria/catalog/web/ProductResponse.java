package com.jhanantezana.jugueria.catalog.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.internal.ProductDetails;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.Money;

record ProductResponse(UUID id, String name, @Nullable String description, UUID categoryId, Money price,
		int displayOrder, boolean quickSalePinned, boolean active, boolean available, @Nullable String imageUrl,
		List<Allergen> allergens, List<UUID> modifierGroupIds, String etag) {

	static ProductResponse from(ProductDetails product, ProductImageUrls imageUrls) {
		return new ProductResponse(product.id(), product.name(), product.description(), product.categoryId(),
				product.price(), product.displayOrder(), product.quickSalePinned(), product.active(),
				product.available(), imageUrls.urlFor(product.imageKey()), product.allergens(),
				product.modifierGroupIds(), ETags.format(product.version()));
	}

}
