package com.jhanantezana.jugueria.catalog;

import java.util.List;

public record ModifierGroupSnapshot(String name, boolean required, int minChoices, int maxChoices,
		List<ModifierOptionSnapshot> options) {
}
