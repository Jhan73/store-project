package com.jhanantezana.jugueria.shared;

import java.util.List;

public final class Singletons {

	private Singletons() {
	}

	// A controlled failure instead of List.getFirst() throwing NoSuchElementException on a seeded-but-missing row.
	public static <T> T requireOne(List<T> rows) {
		if (rows.isEmpty()) {
			throw new IllegalStateException("Expected exactly one seeded row but found none");
		}
		return rows.get(0);
	}

}
