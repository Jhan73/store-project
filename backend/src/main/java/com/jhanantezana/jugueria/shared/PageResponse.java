package com.jhanantezana.jugueria.shared;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public PageResponse {
		content = content == null ? List.of() : List.copyOf(content);
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		return new PageResponse<>(page.getContent().stream().<T>map(mapper).toList(), page.getNumber(),
				page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

}
