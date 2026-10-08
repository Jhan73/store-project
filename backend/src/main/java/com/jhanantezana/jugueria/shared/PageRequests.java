package com.jhanantezana.jugueria.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

	public static final int MAX_PAGE_SIZE = 100;

	private PageRequests() {
	}

	// Out-of-range paging is clamped instead of rejected: the client still gets a valid page. The page is capped so
	// the offset fits an int, which Spring Data requires.
	public static PageRequest of(int page, int size, Sort sort) {
		var boundedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return PageRequest.of(Math.clamp(page, 0, Integer.MAX_VALUE / boundedSize), boundedSize, sort);
	}

	public static PageRequest of(int page, int size) {
		return of(page, size, Sort.unsorted());
	}

}
