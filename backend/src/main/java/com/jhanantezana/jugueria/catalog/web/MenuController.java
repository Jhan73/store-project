package com.jhanantezana.jugueria.catalog.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.MenuCache;
import com.jhanantezana.jugueria.shared.ReturnsETag;

import jakarta.annotation.security.PermitAll;

@RestController
@RequestMapping("/api/v1/catalog")
class MenuController {

	private final MenuCache menuCache;

	private final MenuRenderer renderer;

	MenuController(MenuCache menuCache, MenuRenderer renderer) {
		this.menuCache = menuCache;
		this.renderer = renderer;
	}

	// no-cache makes the browser revalidate every time; the ETag turns that into a cheap 304 (Spring answers a
	// matching If-None-Match itself for a ResponseEntity).
	@GetMapping("/menu")
	@PermitAll
	@ReturnsETag
	ResponseEntity<MenuResponse> menu() {
		var rendered = renderer.render(menuCache.menu());
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(rendered.etag()).body(rendered.body());
	}

}
