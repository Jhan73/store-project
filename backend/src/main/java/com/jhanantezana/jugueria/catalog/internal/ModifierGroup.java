package com.jhanantezana.jugueria.catalog.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.ModifierGroupSnapshot;
import com.jhanantezana.jugueria.shared.BaseEntity;
import com.jhanantezana.jugueria.shared.BusinessException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "catalog", name = "modifier_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModifierGroup extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean required;

	@Column(name = "min_choices", nullable = false)
	private int minChoices;

	@Column(name = "max_choices", nullable = false)
	private int maxChoices;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", nullable = false)
	@OrderBy("displayOrder ASC")
	@BatchSize(size = 50)
	private List<ModifierOption> options = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public ModifierGroup(String name, boolean required, int minChoices, int maxChoices,
			List<ModifierOptionDefinition> definitions, Instant now) {
		validate(required, minChoices, maxChoices, definitions);
		this.name = name;
		this.required = required;
		this.minChoices = minChoices;
		this.maxChoices = maxChoices;
		this.options = new ArrayList<>();
		for (var i = 0; i < definitions.size(); i++) {
			var definition = definitions.get(i);
			options.add(new ModifierOption(definition.name(), definition.priceDelta(), definition.allergens(), i));
		}
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void change(String name, boolean required, int minChoices, int maxChoices,
			List<ModifierOptionDefinition> definitions, Instant now) {
		validate(required, minChoices, maxChoices, definitions);
		var existing = new HashMap<UUID, ModifierOption>();
		options.forEach(option -> existing.put(option.getId(), option));
		var kept = new HashSet<UUID>();
		var added = new ArrayList<ModifierOption>();
		for (var i = 0; i < definitions.size(); i++) {
			var definition = definitions.get(i);
			if (definition.id() == null) {
				added.add(new ModifierOption(definition.name(), definition.priceDelta(), definition.allergens(), i));
				continue;
			}
			var option = existing.get(definition.id());
			if (option == null) {
				throw invalid("Option " + definition.id() + " does not belong to this group");
			}
			if (!kept.add(definition.id())) {
				throw invalid("Option " + definition.id() + " appears more than once");
			}
			option.change(definition.name(), definition.priceDelta(), definition.allergens(), i);
		}
		this.name = name;
		this.required = required;
		this.minChoices = minChoices;
		this.maxChoices = maxChoices;
		options.removeIf(option -> !kept.contains(option.getId()));
		options.addAll(added);
		options.sort(Comparator.comparingInt(ModifierOption::getDisplayOrder));
		this.updatedAt = now;
	}

	// How many options the customer picked from this group; the rule is the same in both channels.
	public void requireSelectionCount(int selected) {
		if (selected < minChoices || selected > maxChoices) {
			throw new BusinessException(CatalogError.INVALID_MODIFIER_SELECTION,
					"Group " + getId() + " needs between " + minChoices + " and " + maxChoices + " choices, got "
							+ selected,
					Map.of("groupId", getId(), "minChoices", minChoices, "maxChoices", maxChoices, "selected",
							selected));
		}
	}

	public ModifierGroupSnapshot snapshot() {
		return new ModifierGroupSnapshot(name, required, minChoices, maxChoices,
				options.stream().map(ModifierOption::snapshot).toList());
	}

	private static void validate(boolean required, int minChoices, int maxChoices,
			List<ModifierOptionDefinition> definitions) {
		if (definitions.isEmpty()) {
			throw invalid("A modifier group needs at least one option");
		}
		if (minChoices < 0 || maxChoices < 1 || minChoices > maxChoices) {
			throw invalid("minChoices must be at least 0, maxChoices at least 1, and minChoices at most maxChoices");
		}
		if (required != (minChoices >= 1)) {
			throw invalid("A required group needs minChoices of at least 1; an optional group needs 0");
		}
		if (minChoices > definitions.size()) {
			throw invalid("minChoices cannot exceed the number of options");
		}
		var names = new HashSet<String>();
		for (var definition : definitions) {
			if (definition.priceDelta().isNegative()) {
				throw new BusinessException(CatalogError.INVALID_PRICE, "An option price must not be negative");
			}
			PriceLimits.requireWithinColumn(definition.priceDelta());
			if (!names.add(definition.name().toLowerCase())) {
				throw invalid("Option names must be unique within the group: " + definition.name());
			}
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(CatalogError.INVALID_MODIFIER_GROUP, detail);
	}

}
