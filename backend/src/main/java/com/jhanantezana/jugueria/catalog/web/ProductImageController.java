package com.jhanantezana.jugueria.catalog.web;

import java.io.IOException;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jhanantezana.jugueria.catalog.internal.ProductDetails;
import com.jhanantezana.jugueria.catalog.internal.ProductImageService;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;

@RestController
@RequestMapping("/api/v1/admin/products/{id}/image")
class ProductImageController {

	private final ProductImageService images;

	private final ProductImageUrls imageUrls;

	ProductImageController(ProductImageService images, ProductImageUrls imageUrls) {
		this.images = images;
		this.imageUrls = imageUrls;
	}

	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.product-not-found", "catalog.invalid-image", "common.content-too-large", "common.unsupported-media-type", "catalog.provider-unavailable" })
	ResponseEntity<ProductResponse> replace(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestPart("file") MultipartFile file) throws IOException {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		return respond(images.replace(id, file.getBytes(), expectedVersion));
	}

	@DeleteMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.product-not-found" })
	ResponseEntity<ProductResponse> remove(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		return respond(images.remove(id, expectedVersion));
	}

	private ResponseEntity<ProductResponse> respond(ProductDetails product) {
		return ResponseEntity.ok().eTag(ETags.format(product.version())).body(ProductResponse.from(product, imageUrls));
	}

}
