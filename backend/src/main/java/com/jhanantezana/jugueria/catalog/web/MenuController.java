package com.jhanantezana.jugueria.catalog.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.MenuCache;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;

import jakarta.annotation.security.PermitAll;

@RestController
@RequestMapping("/api/v1/catalog")
class MenuController {

	private final MenuCache menuCache;

	private final ProductImageUrls imageUrls;

	MenuController(MenuCache menuCache, ProductImageUrls imageUrls) {
		this.menuCache = menuCache;
		this.imageUrls = imageUrls;
	}

	// no-cache makes the browser revalidate every time; the ETag turns that into a cheap 304 (Spring answers a
	// matching If-None-Match itself for a ResponseEntity).
	@GetMapping("/menu")
	@PermitAll
	ResponseEntity<MenuResponse> menu() {
		var snapshot = menuCache.menu();
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noCache())
			.eTag(snapshot.etag())
			.body(MenuResponse.from(snapshot.menu(), imageUrls));
	}

}
