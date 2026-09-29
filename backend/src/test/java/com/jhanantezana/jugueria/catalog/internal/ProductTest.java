package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Money;

class ProductTest {

	static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

	static final Instant LATER = Instant.parse("2026-09-29T11:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void startsActiveAndAvailable() {
		var product = mango("12.50");

		assertThat(product.isActive()).isTrue();
		assertThat(product.isAvailable()).isTrue();
		assertThat(product.getImageKey()).isNull();
		assertThat(product.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsAZeroPrice() {
		assertThatThrownBy(() -> mango("0.00")).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_PRICE));
	}

	@Test
	void rejectsANegativePrice() {
		assertThatThrownBy(() -> mango("-1.00")).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_PRICE));
	}

	@Test
	void changeReplacesTheDefinitionButKeepsAvailabilityAndImage() {
		var product = mango("12.50");
		product.changeImage("products/abc.png", NOW);
		var groupId = UUID.randomUUID();

		product.change("Mango", "Fresh", UUID.randomUUID(), Money.of("14.00", PEN), 3, true, Set.of(Allergen.MILK),
				List.of(groupId), LATER);

		assertThat(product.getName()).isEqualTo("Mango");
		assertThat(product.getPrice()).isEqualTo(Money.of("14.00", PEN));
		assertThat(product.getAllergens()).containsExactly(Allergen.MILK);
		assertThat(product.getModifierGroupIds()).containsExactly(groupId);
		assertThat(product.isQuickSalePinned()).isTrue();
		assertThat(product.getImageKey()).isEqualTo("products/abc.png");
		assertThat(product.isAvailable()).isTrue();
		assertThat(product.getUpdatedAt()).isEqualTo(LATER);
	}

	@Test
	void changeRejectsANonPositivePrice() {
		var product = mango("12.50");

		assertThatThrownBy(() -> product.change("Mango", null, UUID.randomUUID(), Money.of("0.00", PEN), 0, false,
				Set.of(), List.of(), LATER)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_PRICE));
	}

	@Test
	void rejectsTheSameModifierGroupTwice() {
		var groupId = UUID.randomUUID();

		assertThatThrownBy(() -> new Product("Mango", null, UUID.randomUUID(), Money.of("5.00", PEN), 0, false,
				Set.of(), List.of(groupId, groupId), NOW)).isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_PRODUCT));
	}

	@Test
	void deactivatesAndReactivates() {
		var product = mango("12.50");

		product.deactivate(LATER);
		assertThat(product.isActive()).isFalse();

		product.reactivate(LATER);
		assertThat(product.isActive()).isTrue();
	}

	@Test
	void changesAndClearsTheImage() {
		var product = mango("12.50");

		product.changeImage("products/abc.png", LATER);
		assertThat(product.getImageKey()).isEqualTo("products/abc.png");

		product.changeImage(null, LATER);
		assertThat(product.getImageKey()).isNull();
	}

	@Test
	void snapshotDescribesTheProductForTheAuditTrail() {
		var groupId = UUID.randomUUID();
		var product = new Product("Mango", "Fresh", UUID.randomUUID(), Money.of("12.50", PEN), 2, true,
				Set.of(Allergen.SOY, Allergen.MILK), List.of(groupId), NOW);

		var snapshot = product.snapshot();

		assertThat(snapshot.name()).isEqualTo("Mango");
		assertThat(snapshot.price()).isEqualTo(Money.of("12.50", PEN));
		assertThat(snapshot.active()).isTrue();
		assertThat(snapshot.allergens()).containsExactlyInAnyOrder(Allergen.SOY, Allergen.MILK);
		assertThat(snapshot.modifierGroupIds()).containsExactly(groupId);
	}

	private static Product mango(String price) {
		return new Product("Mango", null, UUID.randomUUID(), Money.of(price, PEN), 0, false, Set.of(), List.of(),
				NOW);
	}

}
