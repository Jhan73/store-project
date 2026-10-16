package com.jhanantezana.jugueria.catalog.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.PricedOption;
import com.jhanantezana.jugueria.catalog.PricedSelection;
import com.jhanantezana.jugueria.catalog.ProductCategory;
import com.jhanantezana.jugueria.catalog.QuickSaleItem;
import com.jhanantezana.jugueria.shared.BusinessException;

@Service
class CatalogApiImpl implements CatalogApi {

	private final ProductRepository products;

	private final CategoryRepository categories;

	private final ModifierGroupRepository groups;

	private final AvailabilityService availability;

	CatalogApiImpl(ProductRepository products, CategoryRepository categories, ModifierGroupRepository groups,
			AvailabilityService availability) {
		this.products = products;
		this.categories = categories;
		this.groups = groups;
		this.availability = availability;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean productExists(UUID productId) {
		return products.existsById(productId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<QuickSaleItem> quickSaleItems() {
		return products.findQuickSaleItems()
			.stream()
			.map(product -> new QuickSaleItem(product.getId(), product.getName(), product.getPrice()))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, ProductCategory> categoriesOf(Collection<UUID> productIds) {
		var found = products.findAllById(productIds);
		var categoryById = new HashMap<UUID, Category>();
		categories.findAllById(found.stream().map(Product::getCategoryId).collect(Collectors.toSet()))
			.forEach(category -> categoryById.put(category.getId(), category));
		var result = new HashMap<UUID, ProductCategory>();
		for (var product : found) {
			var category = categoryById.get(product.getCategoryId());
			if (category != null) {
				result.put(product.getId(), new ProductCategory(category.getId(), category.getName()));
			}
		}
		return result;
	}

	@Override
	@Transactional
	public void setProductAvailability(UUID productId, boolean available) {
		availability.setProductAvailability(productId, available);
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
		return new PricedSelection(product.getId(), product.getName(), category.get().getStationId(), total,
				List.copyOf(priced));
	}

	private static BusinessException invalidSelection(String detail) {
		return new BusinessException(CatalogError.INVALID_MODIFIER_SELECTION, detail);
	}

}
