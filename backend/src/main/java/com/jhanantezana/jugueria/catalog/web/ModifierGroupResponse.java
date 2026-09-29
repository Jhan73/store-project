package com.jhanantezana.jugueria.catalog.web;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.internal.ModifierGroup;
import com.jhanantezana.jugueria.catalog.internal.ModifierOption;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.Money;

record ModifierGroupResponse(UUID id, String name, boolean required, int minChoices, int maxChoices,
		List<Option> options, String etag) {

	record Option(UUID id, String name, Money priceDelta, boolean available, List<Allergen> allergens) {

		static Option from(ModifierOption option) {
			return new Option(option.getId(), option.getName(), option.getPriceDelta(), option.isAvailable(),
					option.getAllergens().stream().sorted(Comparator.naturalOrder()).toList());
		}

	}

	static ModifierGroupResponse from(ModifierGroup group) {
		return new ModifierGroupResponse(group.getId(), group.getName(), group.isRequired(), group.getMinChoices(),
				group.getMaxChoices(), group.getOptions().stream().map(Option::from).toList(),
				ETags.format(group.getVersion()));
	}

}
