package com.jhanantezana.jugueria.preparation.web;

import static com.jhanantezana.testsupport.PreparationTables.aLine;
import static com.jhanantezana.testsupport.PreparationTables.aLineAt;
import static com.jhanantezana.testsupport.PreparationTables.anOrder;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.internal.BoardCommands;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.PreparationTables;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardControllerIT {

	static final String BOARD = "/api/v1/board";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PreparationApi preparation;

	@Autowired
	BoardCommands commands;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	// Each endpoint with the card state it needs, so an allowed role gets a 200 and a denied one a 403 before any state check.
	record Endpoint(String name, String method, Function<BoardControllerIT, String> path) {

		@Override
		public String toString() {
			return name;
		}

	}

	static Stream<Arguments> commandEndpoints() {
		return Stream.of(
				Arguments.of(new Endpoint("start", "POST", t -> BOARD + "/orders/" + t.waiting() + "/start")),
				Arguments.of(new Endpoint("ready order", "POST", t -> BOARD + "/orders/" + t.preparing() + "/ready")),
				Arguments.of(new Endpoint("handover", "POST", t -> BOARD + "/orders/" + t.ready() + "/handover")),
				Arguments.of(new Endpoint("recall", "POST", t -> BOARD + "/orders/" + t.ready() + "/recall")),
				Arguments.of(new Endpoint("ready line", "POST", t -> BOARD + "/lines/" + t.preparingLine() + "/ready")),
				Arguments.of(new Endpoint("flag line", "POST", t -> BOARD + "/lines/" + t.preparingLine() + "/flag")),
				Arguments.of(new Endpoint("ack marker", "POST", t -> BOARD + "/markers/" + t.marker() + "/ack")));
	}

	// The kitchen prepares and marks Ready; handing over and recalling stay with the floor.
	static Stream<Arguments> kitchenCommandEndpoints() {
		return commandEndpoints().filter(args -> {
			var name = ((Endpoint) args.get()[0]).name();
			return !name.equals("handover") && !name.equals("recall");
		});
	}

	static Stream<Arguments> floorOnlyCommandEndpoints() {
		return commandEndpoints().filter(args -> {
			var name = ((Endpoint) args.get()[0]).name();
			return name.equals("handover") || name.equals("recall");
		});
	}

	static Stream<Arguments> readEndpoints() {
		return Stream.of(Arguments.of(BOARD + "/tickets"), Arguments.of(BOARD + "/ready"),
				Arguments.of(BOARD + "/all-day"));
	}

	@ParameterizedTest
	@MethodSource("commandEndpoints")
	void staffRolesMayOperateTheBoard(Endpoint endpoint) {
		for (var role : List.of(Role.SERVER, Role.CASHIER, Role.ADMIN)) {
			var result = call(endpoint, AuthenticatedAs.user(UUID.randomUUID(), role));

			assertThat(result).as("%s as %s", endpoint, role).hasStatusOk();
			PreparationTables.clean(jdbc);
		}
	}

	@ParameterizedTest
	@MethodSource("kitchenCommandEndpoints")
	void theKitchenMayPrepareAndMarkReady(Endpoint endpoint) {
		var result = call(endpoint, AuthenticatedAs.user(UUID.randomUUID(), Role.KITCHEN));

		assertThat(result).hasStatusOk();
	}

	@ParameterizedTest
	@MethodSource("floorOnlyCommandEndpoints")
	void theKitchenMayNotHandOverOrRecall(Endpoint endpoint) {
		var result = call(endpoint, AuthenticatedAs.user(UUID.randomUUID(), Role.KITCHEN));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@ParameterizedTest
	@MethodSource("readEndpoints")
	void theKitchenMayReadTheViews(String path) {
		assertThat(mvc.get().uri(path).with(kitchen()).exchange()).hasStatusOk();
	}

	@Test
	void theKitchenMayNotReadTheCustomerDisplay() {
		var result = mvc.get().uri(BOARD + "/display").with(kitchen()).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void aKitchenCardCarriesNoMoneyOrPersonalData() {
		var order = preparation.accept(anOrder(PreparationTables.aLineWithNote("no ice")));
		commands.start(order);

		var tickets = mvc.get().uri(BOARD + "/tickets").with(kitchen()).exchange();
		var ready = mvc.get().uri(BOARD + "/ready").with(kitchen()).exchange();
		var allDay = mvc.get().uri(BOARD + "/all-day").with(kitchen()).exchange();
		var started = post(BOARD + "/orders/" + order + "/ready", kitchen());

		var forbidden = Set.of("price", "unitPrice", "total", "subtotal", "amount", "currency", "payment", "paid",
				"email", "phone", "address", "customer", "customerId", "actorId", "userId", "acknowledgedBy");
		for (var result : List.of(tickets, ready, allDay, started)) {
			assertThat(result).hasStatusOk();
			assertThat(keysOf(result)).doesNotContainAnyElementsOf(forbidden);
		}
		assertThat(keysOf(tickets)).contains("lines", "note", "modifiersText",
				"allergy", "shortNumber");
	}

	@ParameterizedTest
	@MethodSource("commandEndpoints")
	void aCustomerMayNotOperateTheBoard(Endpoint endpoint) {
		var result = call(endpoint, AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@ParameterizedTest
	@MethodSource("commandEndpoints")
	void anAnonymousCallerMayNotOperateTheBoard(Endpoint endpoint) {
		var path = endpoint.path().apply(this);

		assertThat(mvc.post().uri(path).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@ParameterizedTest
	@MethodSource("readEndpoints")
	void staffRolesMayReadTheViews(String path) {
		for (var role : List.of(Role.SERVER, Role.CASHIER, Role.ADMIN)) {
			assertThat(mvc.get().uri(path).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange())
				.as("%s as %s", path, role)
				.hasStatusOk();
		}
	}

	@ParameterizedTest
	@MethodSource("readEndpoints")
	void aCustomerMayNotReadTheViews(String path) {
		var result = mvc.get().uri(path).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@ParameterizedTest
	@MethodSource("readEndpoints")
	void anAnonymousCallerMayNotReadTheViews(String path) {
		assertThat(mvc.get().uri(path).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void ticketsReturnsTheCardsWithLinesAndOpenMarkers() {
		var line = aLine();
		var order = preparation.accept(anOrder(line));

		var result = mvc.get().uri(BOARD + "/tickets").with(server()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(order.toString());
		assertThat(result).bodyJson().extractingPath("$[0].shortNumber").isEqualTo("L-017");
		assertThat(result).bodyJson().extractingPath("$[0].channel").isEqualTo("TABLE");
		assertThat(result).bodyJson().extractingPath("$[0].label").isEqualTo("Table 4");
		assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("WAITING");
		assertThat(result).bodyJson().extractingPath("$[0].lines[0].displayName").isEqualTo("Orange juice");
		assertThat(result).bodyJson().extractingPath("$[0].lines[0].state").isEqualTo("PENDING");
		assertThat(result).bodyJson().extractingPath("$[0].markers[0].kind").isEqualTo("NEW");
	}

	@Test
	void ticketsCanBeFilteredByStation() {
		var station = UUID.randomUUID();
		var order = preparation.accept(anOrder(aLineAt(station)));
		preparation.accept(anOrder(aLine()));

		var result = mvc.get().uri(BOARD + "/tickets").param("station", station.toString()).with(server()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(order.toString());
	}

	@Test
	void readyAndAllDayCanBeFilteredByStationAndAnUnknownStationMatchesNothing() {
		var station = UUID.randomUUID();
		var order = preparation.accept(anOrder(aLineAt(station)));
		commands.start(order);
		commands.readyOrder(order);
		preparation.accept(anOrder(aLineAt(station)));
		preparation.accept(anOrder(aLine()));

		var ready = mvc.get().uri(BOARD + "/ready").param("station", station.toString()).with(server()).exchange();
		var allDay = mvc.get().uri(BOARD + "/all-day").param("station", station.toString()).with(server()).exchange();
		var unknown = mvc.get().uri(BOARD + "/all-day").param("station", UUID.randomUUID().toString()).with(server())
			.exchange();

		assertThat(ready).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(allDay).bodyJson().extractingPath("$.groups[0].quantity").isEqualTo(1);
		assertThat(unknown).bodyJson().extractingPath("$.groups.length()").isEqualTo(0);
	}

	@Test
	void aMalformedStationIsABadRequest() {
		var result = mvc.get().uri(BOARD + "/tickets").param("station", "nope").with(server()).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.malformed-request");
	}

	@Test
	void allDayReturnsCountsAndSeparateLines() {
		preparation.accept(anOrder(aLine()));
		preparation.accept(anOrder(PreparationTables.aLineWithNote("no ice")));

		var result = mvc.get().uri(BOARD + "/all-day").with(server()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.groups[0].quantity").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.individual[0].note").isEqualTo("no ice");
		assertThat(result).bodyJson().extractingPath("$.individual[0].shortNumber").isEqualTo("L-017");
	}

	@Test
	void theWholeFlowGoesThroughTheEndpoints() {
		var order = preparation.accept(anOrder(aLine()));

		var started = post(BOARD + "/orders/" + order + "/start");
		var readied = post(BOARD + "/orders/" + order + "/ready");
		var ready = mvc.get().uri(BOARD + "/ready").with(server()).exchange();
		var handed = post(BOARD + "/orders/" + order + "/handover");

		assertThat(started).bodyJson().extractingPath("$.status").isEqualTo("PREPARING");
		assertThat(readied).bodyJson().extractingPath("$.status").isEqualTo("READY");
		assertThat(readied).bodyJson().extractingPath("$.lines[0].state").isEqualTo("READY");
		assertThat(ready).bodyJson().extractingPath("$[0].id").isEqualTo(order.toString());
		assertThat(handed).bodyJson().extractingPath("$.status").isEqualTo("HANDED_OVER");
		assertThat(handed).bodyJson().extractingPath("$.handedOverAt").isNotNull();
	}

	@Test
	void acknowledgingAMarkerRecordsWhoDidIt() {
		var order = preparation.accept(anOrder(aLine()));
		var marker = PreparationTables.markerId(jdbc, order, "NEW");
		var staff = UUID.randomUUID();

		var result = mvc.post().uri(BOARD + "/markers/" + marker + "/ack")
			.with(AuthenticatedAs.user(staff, Role.SERVER))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.acknowledgedAt").isNotNull();
		assertThat(jdbc.sql("select acknowledged_by from preparation.board_marker where id = ?")
			.param(marker)
			.query(UUID.class)
			.single()).isEqualTo(staff);
	}

	@Test
	void startingAnOrderTwiceIsAConflict() {
		var order = preparation.accept(anOrder(aLine()));
		post(BOARD + "/orders/" + order + "/start");

		var result = post(BOARD + "/orders/" + order + "/start");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("preparation.order-already-started");
	}

	@Test
	void anUnknownOrderLineOrMarkerIsNotFound() {
		var unknown = UUID.randomUUID();

		assertThat(post(BOARD + "/orders/" + unknown + "/start")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(BOARD + "/lines/" + unknown + "/ready")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(BOARD + "/lines/" + unknown + "/flag")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(BOARD + "/markers/" + unknown + "/ack")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(BOARD + "/orders/" + unknown + "/ready")).bodyJson()
			.extractingPath("$.code")
			.isEqualTo("preparation.order-not-found");
	}

	@Test
	void flaggingALineShowsItFlaggedOnTheCard() {
		var order = preparation.accept(anOrder(aLine()));
		var line = PreparationTables.lineIds(jdbc, order).getFirst();

		var result = post(BOARD + "/lines/" + line + "/flag");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.lines[0].flaggedUnavailable").isEqualTo(true);
	}

	@ParameterizedTest
	@EnumSource(com.jhanantezana.jugueria.preparation.PreparationError.class)
	void everyErrorHasAPreparationCodeAndAClientOrConflictStatus(
			com.jhanantezana.jugueria.preparation.PreparationError error) {
		assertThat(error.code()).startsWith("preparation.").matches("[a-z.-]+");
		assertThat(error.status()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT);
	}

	private MvcTestResult call(Endpoint endpoint, org.springframework.test.web.servlet.request.RequestPostProcessor user) {
		return mvc.post().uri(endpoint.path().apply(this)).with(user).exchange();
	}

	private MvcTestResult post(String path) {
		return post(path, server());
	}

	private MvcTestResult post(String path, org.springframework.test.web.servlet.request.RequestPostProcessor user) {
		return mvc.post().uri(path).with(user).exchange();
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor server() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER);
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor kitchen() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.KITCHEN);
	}

	private static Set<String> keysOf(MvcTestResult result) {
		var keys = new HashSet<String>();
		try {
			collectKeys(JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()), keys);
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
		return keys;
	}

	private static void collectKeys(JsonNode node, Set<String> keys) {
		if (node.isObject()) {
			node.properties().forEach(entry -> {
				keys.add(entry.getKey());
				collectKeys(entry.getValue(), keys);
			});
		}
		else if (node.isArray()) {
			node.forEach(child -> collectKeys(child, keys));
		}
	}

	UUID waiting() {
		return preparation.accept(anOrder(aLine()));
	}

	UUID preparing() {
		var order = waiting();
		commands.start(order);
		return order;
	}

	UUID ready() {
		var order = preparing();
		commands.readyOrder(order);
		return order;
	}

	UUID preparingLine() {
		var order = preparing();
		return PreparationTables.lineIds(jdbc, order).getFirst();
	}

	UUID marker() {
		return PreparationTables.markerId(jdbc, waiting(), "NEW");
	}

}
