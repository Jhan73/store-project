package com.jhanantezana.jugueria.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

	public static final int MAX_PAGE_SIZE = 100;

	private PageRequests() {
	}

	// Out-of-range paging is clamped instead of rejected: the client still gets a valid page.
	public static PageRequest of(int page, int size, Sort sort) {
		return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
	}

	public static PageRequest of(int page, int size) {
		return of(page, size, Sort.unsorted());
	}

}
