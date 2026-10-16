package com.jhanantezana.jugueria.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.StockAdjusted;
import com.jhanantezana.jugueria.inventory.StockChangeKind;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class StockControllerIT {

	static final String STOCK = "/api/v1/admin/stock-items";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	InventoryApi inventory;

	@BeforeEach
	void reasons() {
		InventoryTables.anAdjustmentReason(jdbc, "Damaged");
		InventoryTables.anAdjustmentReason(jdbc, "Other");
	}

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void startsTrackingAProductAtZeroAndHidesItUntilItIsStocked() {
		var product = InventoryTables.aProduct(jdbc);

		var result = track(product, true, 5);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.productId").isEqualTo(product.toString());
		assertThat(result).bodyJson().extractingPath("$.tracked").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(0);
		assertThat(result).bodyJson().extractingPath("$.lowStockThreshold").isEqualTo(5);
		assertThat(result).bodyJson().extractingPath("$.low").isEqualTo(true);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		assertThat(events.stream(StockAdjusted.class)).singleElement().satisfies(event -> {
			assertThat(event.kind()).isEqualTo(StockChangeKind.TRACKING_CHANGED);
			assertThat(event.before().tracked()).isFalse();
			assertThat(event.after().tracked()).isTrue();
		});
		assertThat(auditActions(product)).containsExactly("STOCK_TRACKING_CHANGED");
	}

	@Test
	void repeatingTheSameTrackingChangesNothing() {
		var product = InventoryTables.aProduct(jdbc);
		track(product, true, 5);

		assertThat(track(product, true, 5)).hasStatusOk();

		assertThat(auditActions(product)).hasSize(1);
	}

	@Test
	void reportsAnUnknownProductWhenTracking() {
		var result = track(UUID.randomUUID(), true, 0);

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.product-not-found");
	}

	@Test
	void rejectsStoppingTheTrackingOfAProductWithReservedUnits() {
		var product = trackedWithStock(5);
		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 2)), Duration.ofMinutes(15));

		var result = track(product, false, 0);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.stock-reserved");
		assertThat(get(product)).bodyJson().extractingPath("$.tracked").isEqualTo(true);
	}

	@Test
	void stoppingTheTrackingTakesTheProductOffTheStockList() {
		var product = trackedWithStock(5);

		assertThat(track(product, false, 0)).hasStatusOk();

		assertThat(mvc.get().uri(STOCK).with(admin()).exchange()).bodyJson()
			.extractingPath("$[*].productId")
			.asList()
			.doesNotContain(product.toString());
	}

	@Test
	void rejectsAnInvalidThreshold() {
		var result = track(InventoryTables.aProduct(jdbc), true, -1);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	@Test
	void recordsAnEntryWithItsLedgerRowEventAndAuditRow() {
		var product = tracked(0);
		var admin = UUID.randomUUID();

		var result = entry(product, UUID.randomUUID(), 12, "Supplier delivery", AuthenticatedAs.user(admin, Role.ADMIN));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(12);
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(12);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
		assertThat(movement(product)).isEqualTo("ENTRY 12/0 Supplier delivery " + admin);
		assertThat(events.stream(StockAdjusted.class).filter(event -> event.kind() == StockChangeKind.ENTRY))
			.singleElement()
			.satisfies(event -> {
				assertThat(event.before().onHand()).isZero();
				assertThat(event.after().onHand()).isEqualTo(12);
				assertThat(event.actorId()).isEqualTo(admin);
				assertThat(event.actorRole()).isEqualTo(Role.ADMIN);
			});
		assertThat(auditActions(product)).contains("STOCK_ENTRY");
	}

	@Test
	void aBlankNoteIsStoredAsNoNote() {
		var product = tracked(0);

		entry(product, UUID.randomUUID(), 1, "   ", admin());

		assertThat(jdbc.sql("select count(*) from inventory.stock_movement where product_id = ? and note is null")
			.param(product)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void anEntryRepeatedWithTheSameKeyIsReplayedNotApplied() {
		var product = tracked(0);
		var key = UUID.randomUUID();
		var admin = AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);

		var first = entry(product, key, 5, null, admin);
		var second = entry(product, key, 5, null, admin);

		assertThat(first).hasStatusOk();
		assertThat(second).hasStatusOk();
		assertThat(second).headers().hasHeaderSatisfying("Idempotent-Replayed", values -> assertThat(values).containsExactly("true"));
		assertThat(second).bodyJson().extractingPath("$.onHand").isEqualTo(5);
		assertThat(get(product)).bodyJson().extractingPath("$.onHand").isEqualTo(5);
		assertThat(movementCount(product, "ENTRY")).isEqualTo(1);
	}

	@Test
	void theSameKeyWithAnotherBodyIsRejected() {
		var product = tracked(0);
		var key = UUID.randomUUID();
		var admin = AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
		entry(product, key, 5, null, admin);

		var result = entry(product, key, 6, null, admin);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.idempotency-key-reused");
		assertThat(get(product)).bodyJson().extractingPath("$.onHand").isEqualTo(5);
	}

	@Test
	void aRejectedEntryReleasesItsKey() {
		var product = InventoryTables.aProduct(jdbc);
		var key = UUID.randomUUID();
		var admin = AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
		assertThat(entry(product, key, 5, null, admin)).hasStatus(HttpStatus.NOT_FOUND);
		track(product, true, 0);

		var retried = entry(product, key, 5, null, admin);

		assertThat(retried).hasStatusOk();
		assertThat(retried.getResponse().getHeader("Idempotent-Replayed")).isNull();
	}

	@Test
	void anEntryNeedsAnIdempotencyKey() {
		var product = tracked(0);

		var result = mvc.post()
			.uri(STOCK + "/" + product + "/entries")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"quantity\":1}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.idempotency-key-required");
	}

	@Test
	void rejectsAnInvalidEntry() {
		var product = tracked(0);

		var zero = entry(product, UUID.randomUUID(), 0, null, admin());
		var tooMany = entry(product, UUID.randomUUID(), 1_000_001, null, admin());
		var longNote = entry(product, UUID.randomUUID(), 1, "x".repeat(301), admin());

		assertThat(zero).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(zero).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
		assertThat(tooMany).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(longNote).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void anEntryForAProductWithoutAStockRecordIsNotFound() {
		var result = entry(UUID.randomUUID(), UUID.randomUUID(), 1, null, admin());

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.stock-item-not-found");
	}

	@Test
	void anEntryForAProductNoLongerTrackedIsAConflict() {
		var product = tracked(0);
		track(product, false, 0);

		var result = entry(product, UUID.randomUUID(), 1, null, admin());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.product-not-tracked");
	}

	@Test
	void adjustsStockDownWithAReason() {
		var product = trackedWithStock(10);

		var result = adjust(product, UUID.randomUUID(), -3, "damaged", "Dropped crate", admin());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(7);
		assertThat(jdbc.sql("select reason_code from inventory.stock_movement where product_id = ? and type = 'ADJUSTMENT'")
			.param(product)
			.query(String.class)
			.single()).isEqualTo("Damaged");
		assertThat(jdbc.sql("select reason from audit.audit_log where entity_id = ? and action = 'STOCK_ADJUSTMENT'")
			.param(product)
			.query(String.class)
			.single()).isEqualTo("Damaged: Dropped crate");
	}

	@Test
	void adjustsStockUp() {
		var product = trackedWithStock(1);

		var result = adjust(product, UUID.randomUUID(), 4, "Damaged", null, admin());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(5);
	}

	@Test
	void anAdjustmentCannotTakeUnitsThatAreReserved() {
		var product = trackedWithStock(5);
		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 4)), Duration.ofMinutes(15));

		var result = adjust(product, UUID.randomUUID(), -2, "Damaged", null, admin());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.insufficient-stock");
		assertThat(result).bodyJson().extractingPath("$.productId").isEqualTo(product.toString());
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(1);
	}

	@Test
	void anAdjustmentCannotBeZero() {
		var product = trackedWithStock(5);

		var result = adjust(product, UUID.randomUUID(), 0, "Damaged", null, admin());

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.invalid-quantity");
	}

	@Test
	void anAdjustmentNeedsAReasonFromTheStoresList() {
		var product = trackedWithStock(5);

		var unknown = adjust(product, UUID.randomUUID(), -1, "Because", null, admin());
		var other = adjust(product, UUID.randomUUID(), -1, "Other", null, admin());
		var otherWithNote = adjust(product, UUID.randomUUID(), -1, "Other", "Spilled", admin());

		assertThat(unknown).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("inventory.invalid-reason");
		assertThat(other).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(other).bodyJson().extractingPath("$.code").isEqualTo("inventory.invalid-reason");
		assertThat(otherWithNote).hasStatusOk();
	}

	@Test
	void aDeactivatedReasonCannotBeUsed() {
		var product = trackedWithStock(5);
		jdbc.sql("update store.reason set active = false where code = 'Damaged' and type = 'STOCK_ADJUSTMENT'").update();

		var result = adjust(product, UUID.randomUUID(), -1, "Damaged", null, admin());

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.invalid-reason");
	}

	@Test
	void anAdjustmentRepeatedWithTheSameKeyIsAppliedOnce() {
		var product = trackedWithStock(10);
		var key = UUID.randomUUID();
		var admin = AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);

		adjust(product, key, -3, "Damaged", null, admin);
		var replay = adjust(product, key, -3, "Damaged", null, admin);

		assertThat(replay).headers().hasHeaderSatisfying("Idempotent-Replayed", values -> assertThat(values).containsExactly("true"));
		assertThat(get(product)).bodyJson().extractingPath("$.onHand").isEqualTo(7);
	}

	@Test
	void aMalformedAdjustmentKeyIsABadRequest() {
		var product = trackedWithStock(10);

		var result = mvc.post()
			.uri(STOCK + "/" + product + "/adjustments")
			.with(admin())
			.header("Idempotency-Key", "not-a-uuid")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"quantityDelta\":-1,\"reason\":\"Damaged\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.malformed-request");
	}

	@Test
	void listsTrackedProductsAndTheLowStockOnesEmptiestFirst() {
		var plenty = tracked(0);
		entry(plenty, UUID.randomUUID(), 50, null, admin());
		var running = InventoryTables.aProduct(jdbc);
		track(running, true, 10);
		entry(running, UUID.randomUUID(), 8, null, admin());
		var empty = InventoryTables.aProduct(jdbc);
		track(empty, true, 10);

		var all = mvc.get().uri(STOCK).with(admin()).exchange();
		var low = mvc.get().uri(STOCK + "/low").with(admin()).exchange();

		assertThat(all).bodyJson().extractingPath("$[*].productId").asList()
			.contains(plenty.toString(), running.toString(), empty.toString());
		assertThat(low).hasStatusOk();
		assertThat(low).bodyJson().extractingPath("$[*].productId").asList().containsExactly(empty.toString(),
				running.toString());
		assertThat(low).bodyJson().extractingPath("$[0].low").isEqualTo(true);
	}

	@Test
	void theLowStockListCountsWhatCanStillBeSoldNotWhatIsOnTheShelf() {
		var product = InventoryTables.aProduct(jdbc);
		track(product, true, 3);
		entry(product, UUID.randomUUID(), 10, null, admin());
		assertThat(mvc.get().uri(STOCK + "/low").with(admin()).exchange()).bodyJson()
			.extractingPath("$[*].productId")
			.asList()
			.doesNotContain(product.toString());

		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 8)), Duration.ofMinutes(15));

		assertThat(mvc.get().uri(STOCK + "/low").with(admin()).exchange()).bodyJson()
			.extractingPath("$[*].productId")
			.asList()
			.contains(product.toString());
	}

	@Test
	void showsOneStockItem() {
		var product = trackedWithStock(4);

		var result = get(product);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.onHand").isEqualTo(4);
		assertThat(result).bodyJson().extractingPath("$.reserved").isEqualTo(0);
	}

	@Test
	void reportsAnUnknownStockItem() {
		var result = get(UUID.randomUUID());

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.stock-item-not-found");
	}

	@Test
	void pagesTheLedgerNewestFirst() {
		var product = trackedWithStock(10);
		adjust(product, UUID.randomUUID(), -1, "Damaged", null, admin());
		adjust(product, UUID.randomUUID(), -2, "Damaged", null, admin());

		var firstPage = mvc.get().uri(STOCK + "/" + product + "/movements?page=0&size=2").with(admin()).exchange();
		var secondPage = mvc.get().uri(STOCK + "/" + product + "/movements?page=1&size=2").with(admin()).exchange();

		assertThat(firstPage).hasStatusOk();
		assertThat(firstPage).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
		assertThat(firstPage).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
		assertThat(firstPage).bodyJson().extractingPath("$.content[0].type").isEqualTo("ADJUSTMENT");
		assertThat(firstPage).bodyJson().extractingPath("$.content[0].onHandDelta").isEqualTo(-2);
		assertThat(firstPage).bodyJson().extractingPath("$.content[1].onHandDelta").isEqualTo(-1);
		assertThat(secondPage).bodyJson().extractingPath("$.content[0].type").isEqualTo("ENTRY");
	}

	@Test
	void reportsAnUnknownStockItemWhenReadingItsLedger() {
		var result = mvc.get().uri(STOCK + "/" + UUID.randomUUID() + "/movements").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("inventory.stock-item-not-found");
	}

	@Test
	void onlyAnAdminMayReadOrChangeStock() {
		var product = trackedWithStock(5);
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(STOCK).with(user).exchange()).as("list as %s", role).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.get().uri(STOCK + "/low").with(user).exchange()).as("low as %s", role)
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.get().uri(STOCK + "/" + product).with(user).exchange()).as("get as %s", role)
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.get().uri(STOCK + "/" + product + "/movements").with(user).exchange())
				.as("movements as %s", role)
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(STOCK + "/" + product)
				.with(user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tracked\":true,\"lowStockThreshold\":0}")
				.exchange()).as("track as %s", role).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(entry(product, UUID.randomUUID(), 1, null, user)).as("entry as %s", role)
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(adjust(product, UUID.randomUUID(), -1, "Damaged", null, user)).as("adjust as %s", role)
				.hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(get(product)).bodyJson().extractingPath("$.onHand").isEqualTo(5);
	}

	@Test
	void anAnonymousCallerIsUnauthenticated() {
		assertThat(mvc.get().uri(STOCK).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post()
			.uri(STOCK + "/" + UUID.randomUUID() + "/entries")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"quantity\":1}")
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private UUID tracked(int threshold) {
		var product = InventoryTables.aProduct(jdbc);
		assertThat(track(product, true, threshold)).hasStatusOk();
		return product;
	}

	private UUID trackedWithStock(int quantity) {
		var product = tracked(0);
		assertThat(entry(product, UUID.randomUUID(), quantity, null, admin())).hasStatusOk();
		return product;
	}

	private MvcTestResult track(UUID product, boolean tracked, int threshold) {
		return mvc.put()
			.uri(STOCK + "/" + product)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tracked\":%s,\"lowStockThreshold\":%d}".formatted(tracked, threshold))
			.exchange();
	}

	private MvcTestResult get(UUID product) {
		return mvc.get().uri(STOCK + "/" + product).with(admin()).exchange();
	}

	private MvcTestResult entry(UUID product, UUID key, int quantity, String note, RequestPostProcessor user) {
		return mvc.post()
			.uri(STOCK + "/" + product + "/entries")
			.with(user)
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(note == null ? "{\"quantity\":%d}".formatted(quantity)
					: "{\"quantity\":%d,\"note\":\"%s\"}".formatted(quantity, note))
			.exchange();
	}

	private MvcTestResult adjust(UUID product, UUID key, int delta, String reason, String note,
			RequestPostProcessor user) {
		return mvc.post()
			.uri(STOCK + "/" + product + "/adjustments")
			.with(user)
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(note == null ? "{\"quantityDelta\":%d,\"reason\":\"%s\"}".formatted(delta, reason)
					: "{\"quantityDelta\":%d,\"reason\":\"%s\",\"note\":\"%s\"}".formatted(delta, reason, note))
			.exchange();
	}

	private String movement(UUID product) {
		return jdbc.sql("""
				select type || ' ' || on_hand_delta || '/' || reserved_delta || ' ' || coalesce(note, 'null') || ' '
					|| coalesce(actor_id::text, '') from inventory.stock_movement
				where product_id = ? and type in ('ENTRY', 'ADJUSTMENT') order by occurred_at, id limit 1
				""").param(product).query(String.class).single();
	}

	private long movementCount(UUID product, String type) {
		return jdbc.sql("select count(*) from inventory.stock_movement where product_id = ? and type = ?")
			.param(product)
			.param(type)
			.query(Long.class)
			.single();
	}

	private List<String> auditActions(UUID product) {
		return jdbc.sql("select action from audit.audit_log where entity_id = ? and action like 'STOCK\\_%' "
				+ "order by occurred_at, id")
			.param(product)
			.query(String.class)
			.list();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
