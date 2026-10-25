package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

// A line priced but not yet stored is invisible to the usage check; the lock taken by pricing is what stops the
// delete from passing in that gap.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductDeleteRaceIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	CatalogApi catalog;

	@Autowired
	PlatformTransactionManager transactions;

	final ExecutorService executor = Executors.newFixedThreadPool(2);

	@AfterEach
	void cleanUp() {
		executor.shutdownNow();
		InstoreTables.cleanAll(jdbc);
		InventoryTables.clean(jdbc);
	}

	@Test
	void aDeleteWaitsForALineBeingPricedAndThenSeesItAsUsage() throws Exception {
		var product = InventoryTables.aProduct(jdbc);
		var priced = new CountDownLatch(1);
		var storeTheLine = new CountDownLatch(1);
		var adding = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
			catalog.priceSelection(product, List.of());
			priced.countDown();
			waitFor(storeTheLine);
			aTicketLineFor(product);
		}));
		assertThat(priced.await(10, TimeUnit.SECONDS)).isTrue();

		var deleting = executor.submit(() -> mvc.delete()
			.uri("/api/v1/admin/products/" + product)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange());
		await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(5)).until(() -> !deleting.isDone());
		storeTheLine.countDown();

		adding.get(10, TimeUnit.SECONDS);
		var result = deleting.get(10, TimeUnit.SECONDS);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-in-use");
		assertThat(jdbc.sql("select count(*) from catalog.product where id = ?").param(product).query(Long.class).single())
			.isEqualTo(1);
	}

	@Test
	void aLineCannotBePricedForAProductThatWasDeleted() {
		var product = InventoryTables.aProduct(jdbc);
		var result = mvc.delete()
			.uri("/api/v1/admin/products/" + product)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);

		assertThatThrownBy(() -> catalog.priceSelection(product, List.of())).isInstanceOf(BusinessException.class)
			.hasMessageContaining("Product not found");
	}

	private static void waitFor(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private void aTicketLineFor(UUID product) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		var ticket = UUID.randomUUID();
		jdbc.sql("""
				insert into instore.ticket (id, type, counter_name, status, currency, opened_by, created_at, updated_at)
				values (:id, 'COUNTER', 'Ana', 'OPEN', 'PEN', :user, :now, :now)
				""").param("id", ticket).param("user", UUID.randomUUID()).param("now", now).update();
		jdbc.sql("""
				insert into instore.ticket_line (id, ticket_id, position, product_id, product_name, station_id, quantity,
				    unit_price_amount, unit_price_currency, state, created_at, updated_at)
				values (:id, :ticket, 0, :product, 'Juice', :station, 1, 5.00, 'PEN', 'UNSENT', :now, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("ticket", ticket)
			.param("product", product)
			.param("station", UUID.randomUUID())
			.param("now", now)
			.update();
	}

}
