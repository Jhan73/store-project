package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.PaymentRecorded;
import com.jhanantezana.jugueria.instore.SaleRecorded;
import com.jhanantezana.jugueria.instore.TicketLinesSent;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class QuickSaleIT {

	static final String QUICK_SALES = "/api/v1/quick-sales";

	static final String SHIFTS = "/api/v1/shifts";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	StockService stock;

	UUID cashier = UUID.randomUUID();

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void anOpenShiftAndAProduct() {
		juice = InstoreTables.aSizedProduct(jdbc);
		openShift(cashier);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aQuickSaleSendsThePaidItemsToTheBoardAndClosesTheTicketInOneStep() {
		var body = body("Ana", line(juice.large(), 2), "CASH", "20.00");

		var result = quickSale(cashier, Role.CASHIER, UUID.randomUUID(), body);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		var id = idOf(result);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/tickets/" + id);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(result).bodyJson().extractingPath("$.quickSale").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("COUNTER");
		assertThat(result).bodyJson().extractingPath("$.counterName").isEqualTo("Ana");
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("16.00");
		assertThat(result).bodyJson().extractingPath("$.payments[0].change.amount").isEqualTo("4.00");
		assertThat(result).bodyJson().extractingPath("$.lines[0].state").isEqualTo("SENT");
		var card = jdbc.sql("select short_number || '/' || channel || '/' || label from preparation.board_order "
				+ "where source = 'QUICK_SALE' and source_id = ?").param(UUID.fromString(id)).query(String.class).single();
		assertThat(card).matches("Q\\d+/COUNTER/Ana");
		assertThat(PreparationTables.count(jdbc, "board_line")).isEqualTo(1);
		assertThat(events.stream(TicketLinesSent.class)).hasSize(1);
		assertThat(events.stream(PaymentRecorded.class)).singleElement()
			.satisfies(recorded -> assertThat(recorded.ticketClosed()).isTrue());
		assertThat(events.stream(SaleRecorded.class)).singleElement()
			.satisfies(sale -> assertThat(sale.quickSale()).isTrue());
	}

	@Test
	void theCustomerNameIsOptional() {
		var result = quickSale(cashier, Role.CASHIER, UUID.randomUUID(), body(null, line(juice.small(), 1), "CARD", null));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.counterName").isNull();
		var label = jdbc.sql("select label from preparation.board_order where source = 'QUICK_SALE'")
			.query((rs, row) -> rs.getString("label"))
			.list();
		assertThat(label).containsExactly((String) null);
	}

	@Test
	void theSaleIsPaidInFullWithTheChosenMethod() {
		var result = quickSale(cashier, Role.CASHIER, UUID.randomUUID(),
				body(null, line(juice.small(), 1) + "," + line(juice.large(), 1), "WALLET", null));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.payments[0].method").isEqualTo("WALLET");
		assertThat(result).bodyJson().extractingPath("$.payments[0].paid.amount").isEqualTo("13.00");
	}

	@Test
	void aCashSaleNeedsTheAmountReceivedAndOtherMethodsMustNotStateOne() {
		assertProblem(quickSale(cashier, Role.CASHIER, UUID.randomUUID(), body(null, line(juice.small(), 1), "CASH", null)),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertProblem(quickSale(cashier, Role.CASHIER, UUID.randomUUID(), body(null, line(juice.small(), 1), "CASH", "4.00")),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertProblem(quickSale(cashier, Role.CASHIER, UUID.randomUUID(), body(null, line(juice.small(), 1), "CARD", "5.00")),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertThat(ticketCount()).isZero();
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
	}

	@Test
	void aQuickSaleNeedsTheCashiersOpenShiftAndLeavesNothingBehindWithout() {
		var other = UUID.randomUUID();

		var result = quickSale(other, Role.CASHIER, UUID.randomUUID(), body(null, line(juice.small(), 1), "CARD", null));

		assertProblem(result, HttpStatus.CONFLICT, "instore.no-open-shift");
		assertThat(ticketCount()).isZero();
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
	}

	@Test
	void aSaleTheShelfCannotCoverIsRejectedAsAWholeAndLeavesNoCardOnTheBoard() {
		var bottle = InventoryTables.aProduct(jdbc);
		stock.track(bottle, true, 0);
		stock.recordEntry(bottle, 1, null);

		var result = quickSale(cashier, Role.CASHIER, UUID.randomUUID(),
				body(null, "{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":2,\"note\":null,\"allergy\":false}".formatted(bottle), "CARD", null));

		assertProblem(result, HttpStatus.CONFLICT, "inventory.insufficient-stock");
		assertThat(ticketCount()).isZero();
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
		assertThat(stock.get(bottle).onHand()).isEqualTo(1);
	}

	@Test
	void aBadSelectionRejectsTheWholeSale() {
		var result = quickSale(cashier, Role.CASHIER, UUID.randomUUID(),
				body(null, "{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":1,\"note\":null,\"allergy\":false}".formatted(juice.productId()), "CARD", null));

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "catalog.invalid-modifier-selection");
		assertThat(ticketCount()).isZero();
	}

	@Test
	void retryingTheSaleReplaysTheFirstAnswerAndSellsOnce() {
		var key = UUID.randomUUID();
		var body = body("Ana", line(juice.small(), 1), "CARD", null);
		var first = quickSale(cashier, Role.CASHIER, key, body);

		var retry = quickSale(cashier, Role.CASHIER, key, body);

		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(idOf(retry)).isEqualTo(idOf(first));
		assertThat(ticketCount()).isEqualTo(1);
		assertThat(events.stream(SaleRecorded.class)).hasSize(1);
	}

	@Test
	void aSaleNeedsAKeyAtLeastOneLineAndValidLines() {
		var noKey = mvc.post()
			.uri(QUICK_SALES)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(null, line(juice.small(), 1), "CARD", null))
			.exchange();
		assertProblem(noKey, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
		assertThat(quickSale(cashier, Role.CASHIER, UUID.randomUUID(), body(null, "", "CARD", null)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(quickSale(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"lines\":[{\"productId\":\"%s\",\"quantity\":0,\"note\":null,\"allergy\":false}],\"payment\":{\"method\":\"CARD\"}}".formatted(juice.productId())))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(quickSale(cashier, Role.CASHIER, UUID.randomUUID(), "{\"payment\":{\"method\":\"CARD\"}}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void onlyCashiersAndAdminsMaySell() {
		var body = body(null, line(juice.small(), 1), "CARD", null);

		assertThat(quickSale(UUID.randomUUID(), Role.SERVER, UUID.randomUUID(), body)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(quickSale(UUID.randomUUID(), Role.CUSTOMER, UUID.randomUUID(), body)).hasStatus(HttpStatus.FORBIDDEN);
		var anonymous = mvc.post()
			.uri(QUICK_SALES)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
		assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
		var admin = UUID.randomUUID();
		openShift(admin);
		assertThat(quickSale(admin, Role.ADMIN, UUID.randomUUID(), body)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void theFrequentItemsAreTheOnesTheAdminPinnedThatAreOnSale() {
		var pinned = InventoryTables.aProduct(jdbc);
		var unpinned = InventoryTables.aProduct(jdbc);
		var hidden = InventoryTables.aProduct(jdbc);
		jdbc.sql("update catalog.product set quick_sale_pinned = true, display_order = 2 where id = ?").param(pinned).update();
		jdbc.sql("update catalog.product set quick_sale_pinned = true, display_order = 1, available = false where id = ?")
			.param(hidden)
			.update();
		var second = InventoryTables.aProduct(jdbc);
		jdbc.sql("update catalog.product set quick_sale_pinned = true, display_order = 1 where id = ?").param(second).update();

		var result = mvc.get().uri(QUICK_SALES + "/items").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[*].productId").asList()
			.containsExactly(second.toString(), pinned.toString());
		assertThat(result).bodyJson().extractingPath("$[0].price.amount").isEqualTo("5.00");
		assertThat(unpinned).isNotNull();
		assertThat(mvc.get().uri(QUICK_SALES + "/items").with(AuthenticatedAs.user(cashier, Role.SERVER)).exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// helpers

	private void openShift(UUID who) {
		var result = mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	private String line(UUID option, int quantity) {
		return "{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":%d,\"note\":null,\"allergy\":false}".formatted(juice.productId(), option,
				quantity);
	}

	private static String body(String customer, String lines, String method, String received) {
		return "{%s\"lines\":[%s],\"payment\":{\"method\":\"%s\"%s}}".formatted(
				customer == null ? "" : "\"customerName\":\"%s\",".formatted(customer), lines, method,
				received == null ? "" : ",\"receivedAmount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}".formatted(received));
	}

	private MvcTestResult quickSale(UUID actor, Role role, UUID key, String body) {
		return mvc.post()
			.uri(QUICK_SALES)
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private long ticketCount() {
		return InstoreTables.count(jdbc, "ticket");
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
