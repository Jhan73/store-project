package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

/** A modifier option as it was when the line was added: the name and price delta are snapshots. */
public record ChosenOption(UUID optionId, String name, Money priceDelta) {
}
