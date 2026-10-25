package com.jhanantezana.jugueria.catalog.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.internal.ProductDetails;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;
import com.jhanantezana.jugueria.catalog.internal.ProductService;

// Builds product responses, which carry whether the product can still be deleted.
@Component
class ProductResponses {

	private final ProductService products;

	private final ProductImageUrls imageUrls;

	ProductResponses(ProductService products, ProductImageUrls imageUrls) {
		this.products = products;
		this.imageUrls = imageUrls;
	}

	ProductResponse of(ProductDetails product) {
		return all(List.of(product)).get(0);
	}

	List<ProductResponse> all(List<ProductDetails> details) {
		var deletable = products.deletable(details.stream().map(ProductDetails::id).collect(Collectors.toSet()));
		return details.stream()
			.map(product -> ProductResponse.from(product, imageUrls, deletable.contains(product.id())))
			.toList();
	}

}
