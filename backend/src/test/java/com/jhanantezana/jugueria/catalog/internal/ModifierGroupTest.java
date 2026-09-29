package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

class ModifierGroupTest {

	static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void buildsAGroupWithItsOptionsInOrder() {
		var group = group(true, 1, 1, option("Small", "0.00"), option("Large", "2.50"));

		assertThat(group.getOptions()).extracting(ModifierOption::getName).containsExactly("Small", "Large");
		assertThat(group.getOptions()).extracting(ModifierOption::getDisplayOrder).containsExactly(0, 1);
		assertThat(group.getOptions()).allMatch(ModifierOption::isAvailable);
	}

	@Test
	void keepsTheAllergensOfAnOption() {
		var group = group(false, 0, 2, new ModifierOptionDefinition(null, "Peanut butter", price("1.00"),
				Set.of(Allergen.PEANUTS)));

		assertThat(group.getOptions().getFirst().getAllergens()).containsExactly(Allergen.PEANUTS);
	}

	@Test
	void rejectsAGroupWithoutOptions() {
		assertInvalid(() -> new ModifierGroup("Size", true, 1, 1, List.of(), NOW), CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void rejectsAMinimumAboveTheMaximum() {
		assertInvalid(() -> group(true, 2, 1, option("A", "0.00"), option("B", "0.00")),
				CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void rejectsAMaximumBelowOne() {
		assertInvalid(() -> group(false, 0, 0, option("A", "0.00")), CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void aRequiredGroupNeedsAtLeastOneChoice() {
		assertInvalid(() -> group(true, 0, 1, option("A", "0.00")), CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void anOptionalGroupMustAllowZeroChoices() {
		assertInvalid(() -> group(false, 1, 1, option("A", "0.00")), CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void rejectsAMinimumAboveTheNumberOfOptions() {
		assertInvalid(() -> group(true, 2, 2, option("A", "0.00")), CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void rejectsDuplicateOptionNamesIgnoringCase() {
		assertInvalid(() -> group(false, 0, 2, option("Mango", "0.00"), option("mango", "1.00")),
				CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void rejectsANegativeOptionPrice() {
		assertInvalid(() -> group(false, 0, 1, option("Discount", "-1.00")), CatalogError.INVALID_PRICE);
	}

	@Test
	void acceptsAFreeOption() {
		assertThatCode(() -> group(false, 0, 1, option("Plain", "0.00"))).doesNotThrowAnyException();
	}

	@Test
	void acceptsASelectionWithinTheBounds() {
		var group = group(true, 1, 2, option("A", "0.00"), option("B", "0.00"), option("C", "0.00"));

		assertThatCode(() -> group.requireSelectionCount(1)).doesNotThrowAnyException();
		assertThatCode(() -> group.requireSelectionCount(2)).doesNotThrowAnyException();
	}

	@Test
	void rejectsASelectionBelowTheMinimum() {
		var group = group(true, 1, 2, option("A", "0.00"), option("B", "0.00"));

		assertThatThrownBy(() -> group.requireSelectionCount(0)).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_MODIFIER_SELECTION);
			assertThat(e.properties()).containsEntry("groupId", group.getId())
				.containsEntry("minChoices", 1)
				.containsEntry("maxChoices", 2)
				.containsEntry("selected", 0);
		});
	}

	@Test
	void rejectsASelectionAboveTheMaximum() {
		var group = group(false, 0, 1, option("A", "0.00"), option("B", "0.00"));

		assertThatThrownBy(() -> group.requireSelectionCount(2)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CatalogError.INVALID_MODIFIER_SELECTION));
	}

	@Test
	void anOptionalGroupAcceptsNoSelection() {
		var group = group(false, 0, 2, option("A", "0.00"), option("B", "0.00"));

		assertThatCode(() -> group.requireSelectionCount(0)).doesNotThrowAnyException();
	}

	@Test
	void changeKeepsTheOptionsItNamesAndRemovesTheOthers() {
		var group = group(false, 0, 2, option("A", "0.00"), option("B", "1.00"), option("C", "2.00"));
		var keptId = group.getOptions().get(1).getId();

		group.change("Sizes", false, 0, 1, List.of(
				new ModifierOptionDefinition(keptId, "B renamed", price("1.50"), Set.of()),
				option("D", "0.50")), NOW);

		assertThat(group.getName()).isEqualTo("Sizes");
		assertThat(group.getOptions()).extracting(ModifierOption::getName).containsExactly("B renamed", "D");
		assertThat(group.getOptions().getFirst().getId()).isEqualTo(keptId);
		assertThat(group.getOptions().getFirst().getPriceDelta()).isEqualTo(price("1.50"));
		assertThat(group.getOptions()).extracting(ModifierOption::getDisplayOrder).containsExactly(0, 1);
	}

	@Test
	void changeRejectsAnOptionThatBelongsToAnotherGroup() {
		var group = group(false, 0, 1, option("A", "0.00"));

		assertInvalid(() -> group.change("Size", false, 0, 1,
				List.of(new ModifierOptionDefinition(UUID.randomUUID(), "Stranger", price("0.00"), Set.of())), NOW),
				CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void changeRejectsTheSameOptionTwice() {
		var group = group(false, 0, 2, option("A", "0.00"));
		var id = group.getOptions().getFirst().getId();

		assertInvalid(() -> group.change("Size", false, 0, 2, List.of(
				new ModifierOptionDefinition(id, "A", price("0.00"), Set.of()),
				new ModifierOptionDefinition(id, "A2", price("0.00"), Set.of())), NOW),
				CatalogError.INVALID_MODIFIER_GROUP);
	}

	@Test
	void snapshotDescribesTheGroupForTheAuditTrail() {
		var group = group(true, 1, 1, option("Small", "0.00"));

		var snapshot = group.snapshot();

		assertThat(snapshot.name()).isEqualTo("Size");
		assertThat(snapshot.minChoices()).isEqualTo(1);
		assertThat(snapshot.options()).singleElement().satisfies(option -> {
			assertThat(option.name()).isEqualTo("Small");
			assertThat(option.available()).isTrue();
		});
	}

	private static ModifierGroup group(boolean required, int min, int max, ModifierOptionDefinition... options) {
		return new ModifierGroup("Size", required, min, max, List.of(options), NOW);
	}

	private static ModifierOptionDefinition option(String name, String delta) {
		return new ModifierOptionDefinition(null, name, price(delta), Set.of());
	}

	private static Money price(String amount) {
		return Money.of(amount, PEN);
	}

	private static void assertInvalid(Runnable action, CatalogError expected) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(expected));
	}

}
