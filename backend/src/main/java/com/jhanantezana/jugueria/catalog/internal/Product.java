package com.jhanantezana.jugueria.catalog.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.ProductSnapshot;
import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "catalog", name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	@Column(nullable = false)
	private String name;

	private String description;

	private Money price;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(nullable = false)
	private boolean active;

	// Written only by the conditional availability update, so a stale admin save never undoes a staff 86.
	@Column(nullable = false, updatable = false)
	private boolean available;

	@Column(name = "quick_sale_pinned", nullable = false)
	private boolean quickSalePinned;

	@Column(name = "image_key")
	private String imageKey;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(schema = "catalog", name = "product_allergen", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "allergen_code", nullable = false)
	@Enumerated(EnumType.STRING)
	@BatchSize(size = 50)
	private Set<Allergen> allergens = new HashSet<>();

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(schema = "catalog", name = "product_modifier_group",
			joinColumns = @JoinColumn(name = "product_id"))
	@OrderColumn(name = "display_order")
	@Column(name = "group_id", nullable = false)
	@BatchSize(size = 50)
	private List<UUID> modifierGroupIds = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public Product(String name, @Nullable String description, UUID categoryId, Money price, int displayOrder,
			boolean quickSalePinned, Set<Allergen> allergens, List<UUID> modifierGroupIds, Instant now) {
		validate(price, modifierGroupIds);
		this.name = name;
		this.description = description;
		this.categoryId = categoryId;
		this.price = price;
		this.displayOrder = displayOrder;
		this.quickSalePinned = quickSalePinned;
		this.allergens = new HashSet<>(allergens);
		this.modifierGroupIds = new ArrayList<>(modifierGroupIds);
		this.active = true;
		this.available = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	// A full replacement: availability, activation, and the image are not part of the definition.
	public void change(String name, @Nullable String description, UUID categoryId, Money price, int displayOrder,
			boolean quickSalePinned, Set<Allergen> allergens, List<UUID> modifierGroupIds, Instant now) {
		validate(price, modifierGroupIds);
		this.name = name;
		this.description = description;
		this.categoryId = categoryId;
		this.price = price;
		this.displayOrder = displayOrder;
		this.quickSalePinned = quickSalePinned;
		this.allergens.retainAll(allergens);
		this.allergens.addAll(allergens);
		if (!this.modifierGroupIds.equals(modifierGroupIds)) {
			this.modifierGroupIds.clear();
			this.modifierGroupIds.addAll(modifierGroupIds);
		}
		this.updatedAt = now;
	}

	public void deactivate(Instant now) {
		this.active = false;
		this.updatedAt = now;
	}

	public void reactivate(Instant now) {
		this.active = true;
		this.updatedAt = now;
	}

	public void changeImage(@Nullable String imageKey, Instant now) {
		this.imageKey = imageKey;
		this.updatedAt = now;
	}

	public ProductSnapshot snapshot() {
		return new ProductSnapshot(name, description, categoryId, price, displayOrder, quickSalePinned, active,
				imageKey, Set.copyOf(allergens), List.copyOf(modifierGroupIds));
	}

	private static void validate(Money price, List<UUID> modifierGroupIds) {
		if (price.isNegative() || price.isZero()) {
			throw new BusinessException(CatalogError.INVALID_PRICE, "A product price must be greater than zero");
		}
		if (new HashSet<>(modifierGroupIds).size() != modifierGroupIds.size()) {
			throw new BusinessException(CatalogError.INVALID_PRODUCT, "A modifier group can be attached only once");
		}
	}

}
