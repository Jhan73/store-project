package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;

/** A draft priced by the catalog at the moment of asking: the snapshot a line stores. */
record LinePrice(UUID productId, String productName, UUID stationId, Money unitPrice, List<ChosenOption> options) {
}
