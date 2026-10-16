package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import com.jhanantezana.jugueria.catalog.QuickSaleItem;
import com.jhanantezana.jugueria.shared.Money;

record QuickSaleItemResponse(UUID productId, String name, Money price) {

	static QuickSaleItemResponse from(QuickSaleItem item) {
		return new QuickSaleItemResponse(item.productId(), item.name(), item.price());
	}

}
