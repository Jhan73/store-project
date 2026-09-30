package com.jhanantezana.jugueria.catalog.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.internal.MenuView;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;
import com.jhanantezana.jugueria.shared.Money;

import io.swagger.v3.oas.annotations.media.Schema;

record MenuResponse(List<Category> categories) {

	@Schema(name = "MenuCategory")
	record Category(UUID id, String name, List<Product> products) {
	}

	@Schema(name = "MenuProduct")
	record Product(UUID id, String name, @Nullable String description, Money price, @Nullable String imageUrl,
			boolean available, List<Allergen> allergens, List<Group> modifierGroups) {
	}

	@Schema(name = "MenuModifierGroup")
	record Group(UUID id, String name, boolean required, int minChoices, int maxChoices, List<Option> options) {
	}

	@Schema(name = "MenuModifierOption")
	record Option(UUID id, String name, Money priceDelta, boolean available, List<Allergen> allergens) {
	}

	static MenuResponse from(MenuView menu, ProductImageUrls imageUrls) {
		return new MenuResponse(menu.categories()
			.stream()
			.map(category -> new Category(category.id(), category.name(),
					category.products().stream().map(product -> product(product, imageUrls)).toList()))
			.toList());
	}

	private static Product product(MenuView.Product product, ProductImageUrls imageUrls) {
		return new Product(product.id(), product.name(), product.description(), product.price(),
				imageUrls.urlFor(product.imageKey()), product.available(), product.allergens(),
				product.modifierGroups().stream().map(MenuResponse::group).toList());
	}

	private static Group group(MenuView.Group group) {
		return new Group(group.id(), group.name(), group.required(), group.minChoices(), group.maxChoices(),
				group.options()
					.stream()
					.map(option -> new Option(option.id(), option.name(), option.priceDelta(), option.available(),
							option.allergens()))
					.toList());
	}

}
