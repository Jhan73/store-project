package com.jhanantezana.jugueria.catalog.internal;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.ModifierOptionSnapshot;
import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.Money;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "catalog", name = "modifier_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModifierOption extends BaseEntity {

	@Column(nullable = false)
	private String name;

	private Money priceDelta;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	// Written only by the conditional availability update, so a stale admin save never undoes a staff 86.
	@Column(nullable = false, updatable = false)
	private boolean available;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(schema = "catalog", name = "option_allergen", joinColumns = @JoinColumn(name = "option_id"))
	@Column(name = "allergen_code", nullable = false)
	@Enumerated(EnumType.STRING)
	@BatchSize(size = 50)
	private Set<Allergen> allergens = new HashSet<>();

	ModifierOption(String name, Money priceDelta, Set<Allergen> allergens, int displayOrder) {
		this.name = name;
		this.priceDelta = priceDelta;
		this.allergens = new HashSet<>(allergens);
		this.displayOrder = displayOrder;
		this.available = true;
	}

	void change(String name, Money priceDelta, Set<Allergen> allergens, int displayOrder) {
		this.name = name;
		this.priceDelta = priceDelta;
		this.allergens.retainAll(allergens);
		this.allergens.addAll(allergens);
		this.displayOrder = displayOrder;
	}

	ModifierOptionSnapshot snapshot() {
		return new ModifierOptionSnapshot(getId(), name, priceDelta, available, Set.copyOf(allergens));
	}

}
