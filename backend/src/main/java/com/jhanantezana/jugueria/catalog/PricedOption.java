package com.jhanantezana.jugueria.catalog;

import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

public record PricedOption(UUID optionId, UUID groupId, String name, Money priceDelta) {
}
