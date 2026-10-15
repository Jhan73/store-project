package com.jhanantezana.jugueria.preparation.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.internal.DisplayView;

// An order number and, when the order has a person's name, its first word: nothing else reaches a customer screen.
record DisplayEntryResponse(String shortNumber, @Nullable String firstName) {

	static DisplayEntryResponse from(DisplayView.Entry entry) {
		return new DisplayEntryResponse(entry.shortNumber(), entry.firstName());
	}

}
