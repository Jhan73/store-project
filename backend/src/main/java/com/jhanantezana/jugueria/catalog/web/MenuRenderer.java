package com.jhanantezana.jugueria.catalog.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.internal.MenuView;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;

import tools.jackson.databind.json.JsonMapper;

// The ETag is a hash of the body actually sent, so anything that changes the body (data, image base URL, response
// shape) changes it. Only the last menu is remembered: the cache holds one menu at a time.
@Component
class MenuRenderer {

	record Rendered(MenuView source, MenuResponse body, String etag) {
	}

	private final ProductImageUrls imageUrls;

	private final JsonMapper mapper;

	private volatile Rendered last;

	MenuRenderer(ProductImageUrls imageUrls, JsonMapper mapper) {
		this.imageUrls = imageUrls;
		this.mapper = mapper;
	}

	Rendered render(MenuView menu) {
		var remembered = last;
		if (remembered != null && remembered.source() == menu) {
			return remembered;
		}
		var body = MenuResponse.from(menu, imageUrls);
		var rendered = new Rendered(menu, body, etagOf(body));
		last = rendered;
		return rendered;
	}

	private String etagOf(MenuResponse body) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body));
			return HexFormat.of().formatHex(digest, 0, 16);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", e);
		}
	}

}
