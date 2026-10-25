package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.catalog.internal.ProductImageStorage;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ProductDeleteIT {

	static final String PRODUCTS = "/api/v1/admin/products";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@MockitoBean
	ProductImageStorage storage;

	@BeforeEach
	void resetStorage() {
		reset(storage);
	}

	@AfterEach
	void cleanUp() {
		jdbc.sql("delete from reporting.sales_line").update();
		InstoreTables.cleanAll(jdbc);
		InventoryTables.clean(jdbc);
	}

	@Test
	void deletesAnUnusedProductAndPublishesItsSnapshot() {
		var product = InventoryTables.aProduct(jdbc);

		var result = delete(product, "\"0\"");

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(exists(product)).isFalse();
		assertThat(events.stream(ProductChanged.class))
			.singleElement()
			.satisfies(event -> {
				assertThat(event.after()).isNull();
				assertThat(event.before()).isNotNull();
				assertThat(event.productId()).isEqualTo(product);
			});
	}

	@Test
	void deletesTheAllergensAndGroupAttachmentsWithTheProduct() {
		var sized = InstoreTables.aSizedProduct(jdbc);
		jdbc.sql("insert into catalog.product_allergen (product_id, allergen_code) values (?, 'MILK')")
			.param(sized.productId())
			.update();

		assertThat(delete(sized.productId(), "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(jdbc.sql("select count(*) from catalog.product_allergen where product_id = ?")
			.param(sized.productId())
			.query(Long.class)
			.single()).isZero();
		assertThat(jdbc.sql("select count(*) from catalog.product_modifier_group where product_id = ?")
			.param(sized.productId())
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void theListSaysWhichProductsCanBeDeleted() {
		var unused = InventoryTables.aProduct(jdbc);
		var sold = InventoryTables.aProduct(jdbc);
		aTicketLineFor(sold);

		var result = mvc.get().uri(PRODUCTS + "?size=100").with(admin()).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$.content[?(@.id=='%s')].deletable".formatted(unused))
			.asList()
			.containsExactly(true);
		assertThat(result).bodyJson()
			.extractingPath("$.content[?(@.id=='%s')].deletable".formatted(sold))
			.asList()
			.containsExactly(false);
	}

	@Test
	void aSingleProductAlsoSaysIfItCanBeDeleted() {
		var product = InventoryTables.aProduct(jdbc);

		assertThat(mvc.get().uri(PRODUCTS + "/" + product).with(admin()).exchange()).bodyJson()
			.extractingPath("$.deletable")
			.isEqualTo(true);
		aTicketLineFor(product);
		assertThat(mvc.get().uri(PRODUCTS + "/" + product).with(admin()).exchange()).bodyJson()
			.extractingPath("$.deletable")
			.isEqualTo(false);
	}

	@Test
	void aProductOnATicketLineIsOnlyDeactivated() {
		assertInUse(this::aTicketLineFor);
	}

	@Test
	void aProductOnTheBoardIsOnlyDeactivated() {
		assertInUse(product -> {
			var now = OffsetDateTime.now(ZoneOffset.UTC);
			var order = UUID.randomUUID();
			jdbc.sql("""
					insert into preparation.board_order (id, source, source_id, short_number, channel, status, sent_at,
					    created_at, updated_at)
					values (:id, 'TICKET', :source, 'T1', 'TABLE', 'HANDED_OVER', :now, :now, :now)
					""").param("id", order).param("source", UUID.randomUUID()).param("now", now).update();
			jdbc.sql("""
					insert into preparation.board_line (id, board_order_id, source_line_id, position, product_id,
					    display_name, quantity, modifier_signature, state, created_at, updated_at)
					values (:id, :order, :source, 0, :product, 'Juice', 1, 'sig', 'READY', :now, :now)
					""")
				.param("id", UUID.randomUUID())
				.param("order", order)
				.param("source", UUID.randomUUID())
				.param("product", product)
				.param("now", now)
				.update();
		});
	}

	@Test
	void aProductInTheSalesReportIsOnlyDeactivated() {
		assertInUse(product -> jdbc.sql("""
				insert into reporting.sales_line (id, source_id, line_id, ticket_id, occurred_at, business_date, hour,
				    weekday, channel, ticket_type, quick_sale, product_id, product_name, quantity, unit_price_amount,
				    line_total_amount, currency)
				values (:id, :source, :line, :ticket, now(), current_date, 10, 1, 'IN_STORE', 'TABLE', false, :product,
				    'Juice', 1, 5.00, 5.00, 'PEN')
				""")
			.param("id", UUID.randomUUID())
			.param("source", UUID.randomUUID())
			.param("line", UUID.randomUUID())
			.param("ticket", UUID.randomUUID())
			.param("product", product)
			.update());
	}

	@Test
	void aProductWithStockMovementsIsOnlyDeactivated() {
		assertInUse(product -> {
			trackStock(product);
			jdbc.sql("""
					insert into inventory.stock_movement (id, product_id, type, on_hand_delta, reserved_delta, occurred_at)
					values (:id, :product, 'ENTRY', 5, 0, now())
					""").param("id", UUID.randomUUID()).param("product", product).update();
		});
	}

	@Test
	void aProductWithAStockReservationIsOnlyDeactivated() {
		assertInUse(product -> jdbc.sql("""
				insert into inventory.stock_reservation (id, reference_id, product_id, quantity, status, expires_at,
				    created_at, updated_at)
				values (:id, :reference, :product, 1, 'EXPIRED', now(), now(), now())
				""")
			.param("id", UUID.randomUUID())
			.param("reference", UUID.randomUUID())
			.param("product", product)
			.update());
	}

	@Test
	void aStockItemWithNoHistoryGoesAwayWithTheProduct() {
		var product = InventoryTables.aProduct(jdbc);
		trackStock(product);

		assertThat(delete(product, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

		await().untilAsserted(() -> assertThat(jdbc.sql("select count(*) from inventory.stock_item where product_id = ?")
			.param(product)
			.query(Long.class)
			.single()).isZero());
	}

	@Test
	void aStaleOrMissingIfMatchIsRejectedAndAnUnknownProductIsNotFound() {
		var product = InventoryTables.aProduct(jdbc);

		assertThat(mvc.delete().uri(PRODUCTS + "/" + product).with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(delete(product, "\"7\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(exists(product)).isTrue();
		var unknown = delete(UUID.randomUUID(), "\"0\"");
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-not-found");
	}

	@Test
	void onlyAnAdminMayDelete() {
		var product = InventoryTables.aProduct(jdbc);

		var result = mvc.delete()
			.uri(PRODUCTS + "/" + product)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(exists(product)).isTrue();
	}

	@Test
	void deletesTheImageObjectWhenNoOtherProductSharesIt() {
		var product = InventoryTables.aProduct(jdbc);
		setImage(product, "products/abc.png");

		assertThat(delete(product, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

		verify(storage).delete("products/abc.png");
	}

	@Test
	void keepsTheImageObjectAnotherProductStillUses() {
		var first = InventoryTables.aProduct(jdbc);
		var second = InventoryTables.aProduct(jdbc);
		setImage(first, "products/shared.png");
		setImage(second, "products/shared.png");

		assertThat(delete(first, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

		verify(storage, never()).delete(anyString());
	}

	@Test
	void aFailedImageCleanupDoesNotUndoTheDelete() {
		var product = InventoryTables.aProduct(jdbc);
		setImage(product, "products/gone.png");
		doThrow(new IllegalStateException("storage down")).when(storage).delete(anyString());

		assertThat(delete(product, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(exists(product)).isFalse();
	}

	@Test
	void aUsedProductKeepsItsImageObject() {
		var product = InventoryTables.aProduct(jdbc);
		setImage(product, "products/kept.png");
		aTicketLineFor(product);

		assertThat(delete(product, "\"0\"")).hasStatus(HttpStatus.CONFLICT);

		verify(storage, never()).delete(anyString());
	}

	private void assertInUse(Consumer<UUID> makeUsed) {
		var product = InventoryTables.aProduct(jdbc);
		makeUsed.accept(product);

		var result = delete(product, "\"0\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-in-use");
		assertThat(exists(product)).isTrue();
		assertThat(events.stream(ProductChanged.class)).isEmpty();
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

	private void trackStock(UUID product) {
		jdbc.sql("""
				insert into inventory.stock_item (product_id, tracked, on_hand, reserved, low_stock_threshold, created_at,
				    updated_at)
				values (:product, true, 0, 0, 0, now(), now())
				""").param("product", product).update();
	}

	private void setImage(UUID product, String key) {
		jdbc.sql("update catalog.product set image_key = ? where id = ?").param(key).param(product).update();
	}

	private boolean exists(UUID product) {
		return jdbc.sql("select count(*) from catalog.product where id = ?")
			.param(product)
			.query(Long.class)
			.single() > 0;
	}

	private MvcTestResult delete(UUID id, String etag) {
		return mvc.delete().uri(PRODUCTS + "/" + id).with(admin()).header(HttpHeaders.IF_MATCH, etag).exchange();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
