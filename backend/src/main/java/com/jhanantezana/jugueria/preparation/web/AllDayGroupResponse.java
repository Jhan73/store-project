package com.jhanantezana.jugueria.preparation.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.internal.AllDayView;

record AllDayGroupResponse(UUID productId, String displayName, @Nullable String modifiersText, int quantity) {

	static AllDayGroupResponse from(AllDayView.Group group) {
		return new AllDayGroupResponse(group.productId(), group.displayName(), group.modifiersText(),
				group.quantity());
	}

}
