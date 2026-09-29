package com.jhanantezana.jugueria.catalog.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.catalog.ProductSnapshot;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.StoreApi;

@Service
public class ProductService {

	private final ProductRepository products;

	private final CategoryRepository categories;

	private final ModifierGroupRepository groups;

	private final StoreApi store;

	private final CatalogChanges changes;

	private final CurrentActor currentActor;

	private final Clock clock;

	ProductService(ProductRepository products, CategoryRepository categories, ModifierGroupRepository groups,
			StoreApi store, CatalogChanges changes, CurrentActor currentActor, Clock clock) {
		this.products = products;
		this.categories = categories;
		this.groups = groups;
		this.store = store;
		this.changes = changes;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<ProductDetails> list(@Nullable UUID categoryId, Pageable pageable) {
		var page = categoryId == null ? products.findAll(pageable) : products.findByCategoryId(categoryId, pageable);
		return page.map(ProductDetails::from);
	}

	@Transactional(readOnly = true)
	public ProductDetails get(UUID id) {
		return ProductDetails.from(findOrThrow(id));
	}

	@Transactional
	public ProductDetails create(ProductDefinition definition) {
		requireReferences(definition);
		var now = Instant.now(clock);
		var product = new Product(definition.name().strip(), description(definition), definition.categoryId(),
				definition.price(), definition.displayOrder(), definition.quickSalePinned(), definition.allergens(),
				definition.modifierGroupIds(), now);
		try {
			products.saveAndFlush(product);
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		publish(product, null, now);
		return ProductDetails.from(product);
	}

	@Transactional
	public ProductDetails change(UUID id, ProductDefinition definition, long expectedVersion) {
		var product = findOrThrow(id);
		EntityVersions.requireMatching(product.getVersion(), expectedVersion);
		requireReferences(definition);
		var before = product.snapshot();
		var now = Instant.now(clock);
		product.change(definition.name().strip(), description(definition), definition.categoryId(),
				definition.price(), definition.displayOrder(), definition.quickSalePinned(), definition.allergens(),
				definition.modifierGroupIds(), now);
		try {
			products.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		publish(product, before, now);
		return ProductDetails.from(product);
	}

	@Transactional
	public ProductDetails deactivate(UUID id, long expectedVersion) {
		var product = findOrThrow(id);
		EntityVersions.requireMatching(product.getVersion(), expectedVersion);
		if (!product.isActive()) {
			return ProductDetails.from(product);
		}
		var before = product.snapshot();
		var now = Instant.now(clock);
		product.deactivate(now);
		products.flush();
		publish(product, before, now);
		return ProductDetails.from(product);
	}

	@Transactional
	public ProductDetails reactivate(UUID id, long expectedVersion) {
		var product = findOrThrow(id);
		EntityVersions.requireMatching(product.getVersion(), expectedVersion);
		if (product.isActive()) {
			return ProductDetails.from(product);
		}
		var before = product.snapshot();
		var now = Instant.now(clock);
		product.reactivate(now);
		products.flush();
		publish(product, before, now);
		return ProductDetails.from(product);
	}

	private void requireReferences(ProductDefinition definition) {
		if (!categories.existsById(definition.categoryId())) {
			throw new BusinessException(CatalogError.UNKNOWN_CATEGORY,
					"Category " + definition.categoryId() + " does not exist");
		}
		var groupIds = new HashSet<>(definition.modifierGroupIds());
		if (groups.countByIdIn(groupIds) != groupIds.size()) {
			throw new BusinessException(CatalogError.UNKNOWN_MODIFIER_GROUP, "A listed modifier group does not exist");
		}
		if (!definition.price().currency().equals(store.currency())) {
			throw new BusinessException(CatalogError.CURRENCY_MISMATCH,
					"The price must use the store currency " + store.currency().getCurrencyCode());
		}
	}

	private void publish(Product product, @Nullable ProductSnapshot before, Instant now) {
		changes.publish(new ProductChanged(product.getId(), before, product.snapshot(), currentActor.id(),
				currentActor.role(), now));
	}

	private static @Nullable String description(ProductDefinition definition) {
		var description = definition.description();
		return description == null || description.isBlank() ? null : description.strip();
	}

	private Product findOrThrow(UUID id) {
		return products.findById(id)
			.orElseThrow(() -> new BusinessException(CatalogError.PRODUCT_NOT_FOUND, "Product not found"));
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(CatalogError.PRODUCT_NAME_ALREADY_USED,
				"A product with this name already exists in the category");
	}

}
