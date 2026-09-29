package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.jhanantezana.jugueria.catalog.internal.CatalogProperties;
import com.jhanantezana.jugueria.catalog.internal.MenuView;
import com.jhanantezana.jugueria.catalog.internal.ProductImageUrls;
import com.jhanantezana.jugueria.shared.Money;

import tools.jackson.databind.json.JsonMapper;

class MenuRendererTest {

	static final Currency PEN = Currency.getInstance("PEN");

	final JsonMapper mapper = JsonMapper.builder().build();

	final MenuView menu = new MenuView(List.of(new MenuView.Category(UUID.randomUUID(), "Juices",
			List.of(new MenuView.Product(UUID.randomUUID(), "Mango", null, Money.of("5.00", PEN), "products/a.png",
					true, List.of(), List.of())))));

	@Test
	void theSameMenuAndBaseUrlAlwaysGiveTheSameETag() {
		var first = renderer("https://media.example").render(menu);
		var second = renderer("https://media.example").render(menu);

		assertThat(first.etag()).isEqualTo(second.etag());
	}

	@Test
	void aChangeOfTheImageBaseUrlChangesTheETagBecauseTheBodyChanges() {
		var before = renderer("https://media.example").render(menu);
		var after = renderer("https://cdn.example").render(menu);

		assertThat(after.etag()).isNotEqualTo(before.etag());
	}

	@Test
	void aChangeOfTheMenuChangesTheETag() {
		var renderer = renderer("https://media.example");
		var changed = new MenuView(List.of(new MenuView.Category(UUID.randomUUID(), "Other", List.of())));

		assertThat(renderer.render(changed).etag()).isNotEqualTo(renderer.render(menu).etag());
	}

	@Test
	void rendersACachedMenuOnlyOnce() {
		var renderer = renderer("https://media.example");

		assertThat(renderer.render(menu)).isSameAs(renderer.render(menu));
	}

	private MenuRenderer renderer(String baseUrl) {
		var images = new CatalogProperties.Images(baseUrl, "bucket", "us-east-1", Duration.ofSeconds(10),
				Duration.ofSeconds(5), DataSize.ofMegabytes(2));
		return new MenuRenderer(new ProductImageUrls(new CatalogProperties(images)), mapper);
	}

}
