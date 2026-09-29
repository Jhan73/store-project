package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class CatalogApiIT {

	static final Currency PEN = Currency.getInstance("PEN");

	@Autowired
	CatalogApi catalog;

	@Autowired
	CategoryService categories;

	@Autowired
	ModifierGroupService groups;

	@Autowired
	ProductService products;

	@Autowired
	AvailabilityService availability;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void pricesAProductWithItsChosenOptions() {
		var size = requiredSizeGroup();
		var boosters = optionalBoostersGroup();
		var product = product("Mango", "12.50", size.groupId(), boosters.groupId());

		var priced = catalog.priceSelection(product, List.of(size.large(), boosters.chia(), boosters.ginger()));

		assertThat(priced.productId()).isEqualTo(product);
		assertThat(priced.productName()).isEqualTo("Mango");
		assertThat(priced.unitPrice()).isEqualTo(Money.of("15.50", PEN));
		assertThat(priced.options()).extracting(option -> option.name()).containsExactlyInAnyOrder("Large", "Chia", "Ginger");
		assertThat(priced.options()).extracting(option -> option.groupId())
			.containsExactlyInAnyOrder(size.groupId(), boosters.groupId(), boosters.groupId());
	}

	@Test
	void anOptionalGroupMayBeSkipped() {
		var size = requiredSizeGroup();
		var boosters = optionalBoostersGroup();
		var product = product("Mango", "12.50", size.groupId(), boosters.groupId());

		var priced = catalog.priceSelection(product, List.of(size.small()));

		assertThat(priced.unitPrice()).isEqualTo(Money.of("12.50", PEN));
	}

	@Test
	void aProductWithoutGroupsPricesAsItsBasePrice() {
		var product = product("Water", "3.00");

		assertThat(catalog.priceSelection(product, List.of()).unitPrice()).isEqualTo(Money.of("3.00", PEN));
	}

	@Test
	void rejectsASelectionThatSkipsARequiredGroup() {
		var size = requiredSizeGroup();
		var product = product("Mango", "12.50", size.groupId());

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of())).isInstanceOfSatisfying(
				BusinessException.class, e -> {
					assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_MODIFIER_SELECTION);
					assertThat(e.properties()).containsEntry("groupId", size.groupId())
						.containsEntry("minChoices", 1)
						.containsEntry("selected", 0);
				});
	}

	@Test
	void rejectsMoreChoicesThanAGroupAllows() {
		var size = requiredSizeGroup();
		var product = product("Mango", "12.50", size.groupId());

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of(size.small(), size.large())))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_MODIFIER_SELECTION);
				assertThat(e.properties()).containsEntry("maxChoices", 1).containsEntry("selected", 2);
			});
	}

	@Test
	void rejectsAnOptionOfAGroupTheProductDoesNotHave() {
		var size = requiredSizeGroup();
		var boosters = optionalBoostersGroup();
		var product = product("Mango", "12.50", size.groupId());

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of(size.small(), boosters.chia())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_MODIFIER_SELECTION));
	}

	@Test
	void rejectsTheSameOptionTwice() {
		var boosters = optionalBoostersGroup();
		var product = product("Mango", "12.50", boosters.groupId());

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of(boosters.chia(), boosters.chia())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_MODIFIER_SELECTION));
	}

	@Test
	void rejectsAnUnknownProduct() {
		assertThatThrownBy(() -> catalog.priceSelection(UUID.randomUUID(), List.of())).isInstanceOfSatisfying(
				BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(CatalogError.PRODUCT_NOT_FOUND));
	}

	@Test
	void rejectsAnUnavailableProduct() {
		var product = product("Mango", "12.50");
		availability.setProductAvailability(product, false);

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of())).isInstanceOfSatisfying(
				BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(CatalogError.PRODUCT_UNAVAILABLE));
	}

	@Test
	void rejectsAnInactiveProduct() {
		var product = product("Mango", "12.50");
		var version = products.get(product).version();
		products.deactivate(product, version);

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of())).isInstanceOfSatisfying(
				BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(CatalogError.PRODUCT_UNAVAILABLE));
	}

	@Test
	void rejectsAProductWhoseCategoryIsInactive() {
		var categoryId = categories.create("Cat-" + UUID.randomUUID(), null, 0).getId();
		var product = products.create(new ProductDefinition("Mango", null, categoryId, Money.of("5.00", PEN), 0,
				false, Set.of(), List.of())).id();
		categories.deactivate(categoryId, 0);

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of())).isInstanceOfSatisfying(
				BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(CatalogError.PRODUCT_UNAVAILABLE));
	}

	@Test
	void rejectsAnUnavailableOption() {
		var size = requiredSizeGroup();
		var product = product("Mango", "12.50", size.groupId());
		availability.setOptionAvailability(size.large(), false);

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of(size.large()))).isInstanceOfSatisfying(
				BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CatalogError.MODIFIER_OPTION_UNAVAILABLE));
	}

	@Test
	void seesAnAvailabilityChangeImmediatelyBecauseItNeverReadsTheCache() {
		var product = product("Mango", "12.50");
		catalog.priceSelection(product, List.of());

		availability.setProductAvailability(product, false);

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of())).isInstanceOf(BusinessException.class);
	}

	private record Size(UUID groupId, UUID small, UUID large) {
	}

	private record Boosters(UUID groupId, UUID chia, UUID ginger) {
	}

	private Size requiredSizeGroup() {
		var group = groups.create("Size-" + UUID.randomUUID(), true, 1, 1,
				List.of(option("Small", "0.00"), option("Large", "3.00")));
		return new Size(group.getId(), group.getOptions().get(0).getId(), group.getOptions().get(1).getId());
	}

	private Boosters optionalBoostersGroup() {
		var group = groups.create("Boosters-" + UUID.randomUUID(), false, 0, 2,
				List.of(option("Chia", "0.00"), option("Ginger", "0.00"), option("Spirulina", "1.50")));
		return new Boosters(group.getId(), group.getOptions().get(0).getId(), group.getOptions().get(1).getId());
	}

	private UUID product(String name, String price, UUID... groupIds) {
		var categoryId = categories.create("Cat-" + UUID.randomUUID(), null, 0).getId();
		return products.create(new ProductDefinition(name, null, categoryId, Money.of(price, PEN), 0, false, Set.of(),
				List.of(groupIds))).id();
	}

	private static ModifierOptionDefinition option(String name, String delta) {
		return new ModifierOptionDefinition(null, name, Money.of(delta, PEN), Set.of());
	}

}
