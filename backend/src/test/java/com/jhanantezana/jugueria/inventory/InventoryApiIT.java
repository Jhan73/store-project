package com.jhanantezana.jugueria.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class InventoryApiIT {

	static final Duration TTL = Duration.ofMinutes(15);

	@Autowired
	InventoryApi inventory;

	@Autowired
	StockService stock;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void reservingHoldsUnitsWithoutRemovingThemFromTheShelf() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();

		var expiresAt = inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);

		assertThat(expiresAt).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
		var item = stock.get(product);
		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.reserved()).isEqualTo(4);
		assertThat(item.available()).isEqualTo(6);
		assertThat(ledger(product, "RESERVE")).containsExactly("0/4");
	}

	@Test
	void reservingTwiceForTheSameReferenceHoldsOnce() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();

		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);
		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);

		assertThat(stock.get(product).reserved()).isEqualTo(4);
		assertThat(ledger(product, "RESERVE")).hasSize(1);
	}

	@Test
	void linesOfTheSameProductAreSummed() {
		var product = trackedWithStock(10);

		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 2), new StockLine(product, 3)), TTL);

		assertThat(stock.get(product).reserved()).isEqualTo(5);
	}

	@Test
	void committingTurnsTheReservationIntoASale() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);

		inventory.commit(order);

		var item = stock.get(product);
		assertThat(item.onHand()).isEqualTo(6);
		assertThat(item.reserved()).isZero();
		assertThat(ledger(product, "COMMIT")).containsExactly("-4/-4");
	}

	@Test
	void committingTwiceSellsOnce() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);

		inventory.commit(order);
		inventory.commit(order);

		assertThat(stock.get(product).onHand()).isEqualTo(6);
		assertThat(ledger(product, "COMMIT")).hasSize(1);
	}

	@Test
	void releasingGivesTheUnitsBack() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);

		inventory.release(order);
		inventory.release(order);

		var item = stock.get(product);
		assertThat(item.onHand()).isEqualTo(10);
		assertThat(item.reserved()).isZero();
		assertThat(ledger(product, "RELEASE")).containsExactly("0/-4");
	}

	@Test
	void releasingOrCommittingAReferenceWithNothingReservedDoesNothing() {
		var product = trackedWithStock(10);

		inventory.release(UUID.randomUUID());
		inventory.commit(UUID.randomUUID());

		assertThat(stock.get(product).onHand()).isEqualTo(10);
	}

	@Test
	void committingAReleasedReservationIsRejected() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);
		inventory.release(order);

		assertThatThrownBy(() -> inventory.commit(order)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(InventoryError.RESERVATION_NOT_ACTIVE));
		assertThat(stock.get(product).onHand()).isEqualTo(10);
	}

	@Test
	void aReleasedReferenceCanReserveAgain() {
		var product = trackedWithStock(10);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);
		inventory.release(order);

		inventory.reserve(order, List.of(new StockLine(product, 4)), TTL);
		inventory.commit(order);

		assertThat(stock.get(product).onHand()).isEqualTo(6);
	}

	@Test
	void reservedUnitsCannotBeSoldInStore() {
		var product = trackedWithStock(3);
		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 2)), TTL);

		assertThatThrownBy(() -> inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 2))))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.errorCode()).isEqualTo(InventoryError.INSUFFICIENT_STOCK);
				assertThat(e.properties()).containsEntry("productId", product.toString()).containsEntry("available", 1);
			});
		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1)));
		assertThat(stock.get(product).onHand()).isEqualTo(2);
	}

	@Test
	void aRejectedLineChangesNoneOfTheOthers() {
		var plenty = trackedWithStock(10);
		var scarce = trackedWithStock(1);

		assertThatThrownBy(() -> inventory.deduct(UUID.randomUUID(),
				List.of(new StockLine(plenty, 1), new StockLine(scarce, 2)))).isInstanceOf(BusinessException.class);

		assertThat(stock.get(plenty).onHand()).isEqualTo(10);
		assertThat(ledger(plenty, "SALE")).isEmpty();
	}

	@Test
	void deductingTheSameSaleTwiceSellsOnce() {
		var product = trackedWithStock(10);
		var sale = UUID.randomUUID();

		inventory.deduct(sale, List.of(new StockLine(product, 3)));
		inventory.deduct(sale, List.of(new StockLine(product, 3)));

		assertThat(stock.get(product).onHand()).isEqualTo(7);
		assertThat(ledger(product, "SALE")).containsExactly("-3/0");
	}

	@Test
	void returningUnitsPutsThemBackOnce() {
		var product = trackedWithStock(10);
		var sale = UUID.randomUUID();
		inventory.deduct(sale, List.of(new StockLine(product, 3)));

		inventory.returnUnits(sale, List.of(new StockLine(product, 3)));
		inventory.returnUnits(sale, List.of(new StockLine(product, 3)));

		assertThat(stock.get(product).onHand()).isEqualTo(10);
		assertThat(ledger(product, "RETURN")).containsExactly("3/0");
	}

	@Test
	void productsThatAreNotTrackedAreIgnored() {
		var untracked = InventoryTables.aProduct(jdbc);
		var tracked = trackedWithStock(5);

		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(untracked, 99), new StockLine(tracked, 1)), TTL);
		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(untracked, 99)));
		inventory.returnUnits(UUID.randomUUID(), List.of(new StockLine(untracked, 99)));

		assertThat(stock.get(tracked).reserved()).isEqualTo(1);
		assertThat(InventoryTables.isAvailable(jdbc, untracked)).isTrue();
	}

	@Test
	void aProductNoLongerTrackedIsIgnoredEvenIfItHasHistory() {
		var product = trackedWithStock(5);
		stock.track(product, false, 0);

		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 99)));

		assertThat(stock.get(product).onHand()).isEqualTo(5);
	}

	@Test
	void sellingTheLastUnitMakesTheProductUnavailableAndRestockingRestoresIt() {
		var product = trackedWithStock(2);

		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1)));
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1)));

		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		assertThat(events.stream(StockDepleted.class)).singleElement()
			.satisfies(event -> assertThat(event.productId()).isEqualTo(product));
		stock.recordEntry(product, 5, null);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void reservingEveryUnitHidesTheProductUntilTheReservationIsReleased() {
		var product = trackedWithStock(2);
		var order = UUID.randomUUID();

		inventory.reserve(order, List.of(new StockLine(product, 2)), TTL);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		inventory.release(order);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void returnsRestoreAvailabilityOfADepletedProduct() {
		var product = trackedWithStock(1);
		var sale = UUID.randomUUID();
		inventory.deduct(sale, List.of(new StockLine(product, 1)));
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();

		inventory.returnUnits(sale, List.of(new StockLine(product, 1)));

		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void crossingTheLowStockThresholdPublishesOnce() {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 3);
		stock.recordEntry(product, 5, null);

		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1)));
		assertThat(events.stream(StockLow.class)).isEmpty();
		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1)));
		inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1)));

		assertThat(events.stream(StockLow.class)).singleElement().satisfies(event -> {
			assertThat(event.productId()).isEqualTo(product);
			assertThat(event.available()).isEqualTo(3);
			assertThat(event.threshold()).isEqualTo(3);
		});
	}

	@Test
	void aLineNeedsAPositiveQuantity() {
		assertThatThrownBy(() -> new StockLine(UUID.randomUUID(), 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aReservationNeedsAPositiveTimeToLive() {
		var product = trackedWithStock(5);
		var lines = List.of(new StockLine(product, 1));

		assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), lines, Duration.ZERO))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), lines, Duration.ofMinutes(-1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(stock.get(product).reserved()).isZero();
	}
	@Test
	void returningUnitsOnlyPutsBackWhatTheReferenceSold() {
		var product = trackedWithStock(10);
		var sale = UUID.randomUUID();
		inventory.deduct(sale, List.of(new StockLine(product, 2)));

		inventory.returnUnits(UUID.randomUUID(), List.of(new StockLine(product, 2)));

		assertThat(stock.get(product).onHand()).isEqualTo(8);
		assertThat(ledger(product, "RETURN")).isEmpty();
	}

	@Test
	void aReturnNeverExceedsTheUnitsTheSaleTook() {
		var product = trackedWithStock(10);
		var sale = UUID.randomUUID();
		inventory.deduct(sale, List.of(new StockLine(product, 2)));

		inventory.returnUnits(sale, List.of(new StockLine(product, 5)));

		assertThat(stock.get(product).onHand()).isEqualTo(10);
		assertThat(ledger(product, "RETURN")).containsExactly("2/0");
	}

	@Test
	void aProductThatWasNotTrackedWhenSoldIsNotReturnedOnceItIsTracked() {
		var product = InventoryTables.aProduct(jdbc);
		var sale = UUID.randomUUID();
		inventory.deduct(sale, List.of(new StockLine(product, 3)));
		stock.track(product, true, 0);
		stock.recordEntry(product, 10, null);

		inventory.returnUnits(sale, List.of(new StockLine(product, 3)));

		assertThat(stock.get(product).onHand()).isEqualTo(10);
		assertThat(ledger(product, "RETURN")).isEmpty();
	}

	@Test
	void aProductUntrackedAfterTheSaleIsIgnoredLikeEveryOtherCallOnAnUntrackedProduct() {
		var product = trackedWithStock(10);
		var sale = UUID.randomUUID();
		inventory.deduct(sale, List.of(new StockLine(product, 2)));
		stock.track(product, false, 0);

		inventory.returnUnits(sale, List.of(new StockLine(product, 2)));

		assertThat(ledger(product, "RETURN")).isEmpty();
		assertThat(stock.get(product).onHand()).isEqualTo(8);
	}

	@Test
	void deductingAllowingAShortfallTakesStockBelowZeroWithANormalSaleRow() {
		var product = trackedWithStock(1);
		var sale = UUID.randomUUID();

		inventory.deductAllowingShortfall(sale, List.of(new StockLine(product, 3)));

		assertThat(stock.get(product).onHand()).isEqualTo(-2);
		assertThat(ledger(product, "SALE")).containsExactly("-3/0");
		assertThatThrownBy(() -> inventory.deduct(UUID.randomUUID(), List.of(new StockLine(product, 1))))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void aShortfallSaleIsAppliedOncePerReference() {
		var product = trackedWithStock(1);
		var sale = UUID.randomUUID();

		inventory.deductAllowingShortfall(sale, List.of(new StockLine(product, 3)));
		inventory.deductAllowingShortfall(sale, List.of(new StockLine(product, 3)));

		assertThat(stock.get(product).onHand()).isEqualTo(-2);
		assertThat(ledger(product, "SALE")).hasSize(1);
	}

	@Test
	void aShortfallSaleNeverEatsReservedUnits() {
		var product = trackedWithStock(3);
		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 2)), TTL);

		assertThatThrownBy(() -> inventory.deductAllowingShortfall(UUID.randomUUID(), List.of(new StockLine(product, 2))))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).errorCode().code())
			.isEqualTo("inventory.insufficient-stock");
		inventory.deductAllowingShortfall(UUID.randomUUID(), List.of(new StockLine(product, 1)));

		assertThat(stock.get(product).onHand()).isEqualTo(2);
		assertThat(stock.get(product).reserved()).isEqualTo(2);
	}

	@Test
	void aProductStaysUnavailableWhileItsStockIsNegativeAndComesBackWithAnEntry() {
		var product = trackedWithStock(1);

		inventory.deductAllowingShortfall(UUID.randomUUID(), List.of(new StockLine(product, 3)));

		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		stock.recordEntry(product, 1, null);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		stock.recordEntry(product, 5, null);
		assertThat(stock.get(product).onHand()).isEqualTo(4);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void aShortfallSaleIgnoresProductsThatAreNotTracked() {
		var product = InventoryTables.aProduct(jdbc);

		inventory.deductAllowingShortfall(UUID.randomUUID(), List.of(new StockLine(product, 3)));

		assertThat(ledger(product, "SALE")).isEmpty();
	}


	private UUID trackedWithStock(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private List<String> ledger(UUID product, String type) {
		return jdbc.sql("""
				select on_hand_delta || '/' || reserved_delta from inventory.stock_movement
				where product_id = ? and type = ? order by occurred_at, id
				""").param(product).param(type).query(String.class).list();
	}

}
