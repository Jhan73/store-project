package com.jhanantezana.jugueria.catalog.web;

import java.net.URI;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.ProductDetails;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;
import com.jhanantezana.jugueria.catalog.internal.ProductService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;
import com.jhanantezana.jugueria.shared.PageRequests;
import com.jhanantezana.jugueria.shared.PageResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/products")
class ProductController {

	// The menu order, with the id as a tiebreak so equal names and orders never shuffle between pages.
	private static final Sort SORT = Sort.by("displayOrder").and(Sort.by("name")).and(Sort.by("id"));

	private final ProductService products;

	private final ProductImageUrls imageUrls;

	ProductController(ProductService products, ProductImageUrls imageUrls) {
		this.products = products;
		this.imageUrls = imageUrls;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	PageResponse<ProductResponse> list(@RequestParam(required = false) @Nullable UUID categoryId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		var pageable = PageRequests.of(page, size, SORT);
		return PageResponse.from(products.list(categoryId, pageable), product -> ProductResponse.from(product, imageUrls));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.product-not-found" })
	ProductResponse get(@PathVariable UUID id, HttpServletResponse response) {
		var product = products.get(id);
		// The ETag is the version, which an availability change leaves alone. Returning a bare body keeps Spring from
		// answering a matching If-None-Match with a 304 that would hold a stale `available`.
		response.setHeader(HttpHeaders.ETAG, ETags.format(product.version()));
		response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
		return ProductResponse.from(product, imageUrls);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "catalog.unknown-category", "catalog.unknown-modifier-group", "catalog.currency-mismatch", "catalog.product-name-already-used", "catalog.invalid-price", "catalog.invalid-product" })
	ResponseEntity<ProductResponse> create(@Valid @RequestBody SaveProductRequest request) {
		var product = products.create(request.toDefinition());
		return respond(ResponseEntity.created(URI.create("/api/v1/admin/products/" + product.id())), product);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.product-not-found", "catalog.unknown-category", "catalog.unknown-modifier-group", "catalog.currency-mismatch", "catalog.product-name-already-used", "catalog.invalid-price", "catalog.invalid-product" })
	ResponseEntity<ProductResponse> change(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody SaveProductRequest request) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		return respond(ResponseEntity.ok(), products.change(id, request.toDefinition(), expectedVersion));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.product-not-found" })
	ResponseEntity<ProductResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		return respond(ResponseEntity.ok(), products.deactivate(id, expectedVersion));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.product-not-found" })
	ResponseEntity<ProductResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		return respond(ResponseEntity.ok(), products.reactivate(id, expectedVersion));
	}

	private ResponseEntity<ProductResponse> respond(ResponseEntity.BodyBuilder builder, ProductDetails product) {
		return builder.eTag(ETags.format(product.version())).body(ProductResponse.from(product, imageUrls));
	}

}
