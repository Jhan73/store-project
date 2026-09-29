package com.jhanantezana.jugueria.catalog.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.PricedOption;
import com.jhanantezana.jugueria.catalog.PricedSelection;
import com.jhanantezana.jugueria.shared.BusinessException;

@Service
class CatalogApiImpl implements CatalogApi {

	private final ProductRepository products;

	private final CategoryRepository categories;

	private final ModifierGroupRepository groups;

	CatalogApiImpl(ProductRepository products, CategoryRepository categories, ModifierGroupRepository groups) {
		this.products = products;
		this.categories = categories;
		this.groups = groups;
	}

	@Override
	@Transactional(readOnly = true)
	public PricedSelection priceSelection(UUID productId, List<UUID> optionIds) {
		var product = products.findById(productId)
			.orElseThrow(() -> new BusinessException(CatalogError.PRODUCT_NOT_FOUND, "Product not found"));
		var category = categories.findById(product.getCategoryId());
		if (!product.isActive() || !product.isAvailable() || category.isEmpty() || !category.get().isActive()) {
			throw new BusinessException(CatalogError.PRODUCT_UNAVAILABLE, "The product is not on sale right now");
		}
		if (new HashSet<>(optionIds).size() != optionIds.size()) {
			throw invalidSelection("An option can be chosen only once");
		}

		var productGroups = groups.findAllByIdIn(product.getModifierGroupIds());
		var groupOfOption = new HashMap<UUID, ModifierGroup>();
		var optionById = new HashMap<UUID, ModifierOption>();
		for (var group : productGroups) {
			for (var option : group.getOptions()) {
				groupOfOption.put(option.getId(), group);
				optionById.put(option.getId(), option);
			}
		}

		var chosenPerGroup = new HashMap<UUID, Integer>();
		var priced = new ArrayList<PricedOption>();
		var total = product.getPrice();
		for (var optionId : optionIds) {
			var option = optionById.get(optionId);
			if (option == null) {
				throw invalidSelection("Option " + optionId + " does not belong to this product");
			}
			if (!option.isAvailable()) {
				throw new BusinessException(CatalogError.MODIFIER_OPTION_UNAVAILABLE,
						"Option " + optionId + " is not available right now");
			}
			if (!option.getPriceDelta().currency().equals(total.currency())) {
				throw new BusinessException(CatalogError.PRICE_CURRENCY_MISMATCH,
						"Option " + optionId + " is priced in another currency than the product");
			}
			var group = groupOfOption.get(optionId);
			chosenPerGroup.merge(group.getId(), 1, Integer::sum);
			priced.add(new PricedOption(optionId, group.getId(), option.getName(), option.getPriceDelta()));
			total = total.plus(option.getPriceDelta());
		}
		for (var group : productGroups) {
			group.requireSelectionCount(chosenPerGroup.getOrDefault(group.getId(), 0));
		}
		return new PricedSelection(product.getId(), product.getName(), total, List.copyOf(priced));
	}

	private static BusinessException invalidSelection(String detail) {
		return new BusinessException(CatalogError.INVALID_MODIFIER_SELECTION, detail);
	}

}
