package com.jhanantezana.jugueria.catalog.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.CategoryService;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/categories")
class CategoryController {

	private final CategoryService categories;

	CategoryController(CategoryService categories) {
		this.categories = categories;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<CategoryResponse> list() {
		return categories.list().stream().map(CategoryResponse::from).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
		var category = categories.create(request.name(), request.stationId(), request.displayOrder());
		return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + category.getId()))
			.eTag(ETags.format(category.getVersion()))
			.body(CategoryResponse.from(category));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<CategoryResponse> change(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody UpdateCategoryRequest request) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var category = categories.change(id, request.name(), request.stationId(), request.displayOrder(),
				expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(category.getVersion())).body(CategoryResponse.from(category));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<CategoryResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var category = categories.deactivate(id, expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(category.getVersion())).body(CategoryResponse.from(category));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<CategoryResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var category = categories.reactivate(id, expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(category.getVersion())).body(CategoryResponse.from(category));
	}

}
