package com.jhanantezana.jugueria.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InventoryTables;

/** Staff cannot mark a stock-controlled product available while there is nothing left to sell. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AvailabilityStockGuardIT {

	static final String AVAILABILITY = "/api/v1/catalog/products/%s/availability";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	StockService stock;

	@Autowired
	InventoryApi inventory;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void rejectsAvailableForATrackedProductWithNoStock() {
		var product = tracked(0);

		var result = setAvailable(product, true, Role.SERVER);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.product-out-of-stock");
		assertThat(result).bodyJson().extractingPath("$.productId").isEqualTo(product.toString());
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(0);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
	}

	@Test
	void rejectsAvailableWhileStockIsNegative() {
		var product = tracked(1);
		inventory.deductAllowingShortfall(UUID.randomUUID(), List.of(new StockLine(product, 3)));

		assertThat(setAvailable(product, true, Role.CASHIER)).hasStatus(HttpStatus.CONFLICT);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
	}

	@Test
	void rejectsAvailableWhenEveryUnitIsReserved() {
		var product = tracked(2);
		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 2)), Duration.ofMinutes(10));

		assertThat(setAvailable(product, true, Role.ADMIN)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void allowsAvailableAgainOnceThereIsStockToSell() {
		var product = tracked(3);
		assertThat(setAvailable(product, false, Role.SERVER)).hasStatusOk();

		var result = setAvailable(product, true, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void marksUnavailableWhateverTheStock() {
		var product = tracked(0);

		assertThat(setAvailable(product, false, Role.SERVER)).hasStatusOk();
	}

	@Test
	void doesNotGuardAProductThatIsNotTracked() {
		var product = InventoryTables.aProduct(jdbc);
		assertThat(setAvailable(product, false, Role.SERVER)).hasStatusOk();

		assertThat(setAvailable(product, true, Role.SERVER)).hasStatusOk();
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void stockStillRestoresAvailabilityByItself() {
		var product = tracked(0);

		stock.recordEntry(product, 4, null);

		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	private UUID tracked(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		if (quantity > 0) {
			stock.recordEntry(product, quantity, null);
		}
		return product;
	}

	private MvcTestResult setAvailable(UUID product, boolean available, Role role) {
		return mvc.put()
			.uri(AVAILABILITY.formatted(product))
			.with(AuthenticatedAs.user(UUID.randomUUID(), role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":" + available + "}")
			.exchange();
	}

}
