package com.jhanantezana.jugueria.preparation.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** The customer screen: the cards being prepared and the ones ready, each as a number and, if there is one, a first name. */
public record DisplayView(List<Entry> preparing, List<Entry> ready) {

	public record Entry(String shortNumber, @Nullable String firstName) {
	}

}
