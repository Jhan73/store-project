package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.StationChanged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class StationActivationIT {

	static final String STATIONS = "/api/v1/admin/stations";

	static final String CATEGORIES = "/api/v1/admin/categories";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
		InstoreTables.cleanAll(jdbc);
		CatalogTables.clean(jdbc);
	}

	@Test
	void aNewStationIsActive() {
		var result = createStation("Bar-" + UUID.randomUUID());

		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void deactivatesAndReactivatesAStationPublishingEachChange() {
		var created = createStation("Bar-" + UUID.randomUUID());

		var deactivated = post(idOf(created), "deactivate", etagOf(created));

		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);
		assertThat(deactivated).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");

		var reactivated = post(idOf(created), "reactivate", etagOf(deactivated));

		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(events.stream(StationChanged.class).map(StationChanged::kind))
			.containsExactly(CatalogChangeKind.CREATED, CatalogChangeKind.DEACTIVATED, CatalogChangeKind.ACTIVATED);
	}

	@Test
	void deactivatingAnInactiveStationChangesNothing() {
		var created = createStation("Bar-" + UUID.randomUUID());
		var deactivated = post(idOf(created), "deactivate", etagOf(created));

		var again = post(idOf(created), "deactivate", etagOf(deactivated));

		assertThat(again).hasStatusOk();
		assertThat(events.stream(StationChanged.class)).hasSize(2);
	}

	@Test
	void theDefaultStationCannotBeDeactivated() {
		var defaultId = jdbc.sql("select id from catalog.station where default_station").query(UUID.class).single();

		var result = post(defaultId.toString(), "deactivate", "\"0\"");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.default-station-required");
	}

	@Test
	void refusesToDeactivateWhileAnActiveCategoryUsesTheStation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		createCategory(idOf(station));

		var result = post(idOf(station), "deactivate", etagOf(station));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-has-active-categories");
	}

	@Test
	void anInactiveCategoryDoesNotBlockTheDeactivation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		var category = createCategory(idOf(station));
		deactivateCategory(idOf(category), etagOf(category));

		var result = post(idOf(station), "deactivate", etagOf(station));

		assertThat(result).hasStatusOk();
	}

	@Test
	void refusesToDeactivateWhileACardStillWaitsAtTheStation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		aLiveCardAt(UUID.fromString(idOf(station)), "PENDING", "WAITING");

		var result = post(idOf(station), "deactivate", etagOf(station));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-has-live-orders");
	}

	@Test
	void refusesToDeactivateWhileAnUnsentTicketLineIsPricedForTheStation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		aTicketLineAt(UUID.fromString(idOf(station)), "OPEN", "UNSENT");

		var result = post(idOf(station), "deactivate", etagOf(station));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-has-unsent-lines");
	}

	@Test
	void aSentLineOrACancelledTicketDoesNotBlockTheDeactivation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		aTicketLineAt(UUID.fromString(idOf(station)), "OPEN", "SENT");
		aTicketLineAt(UUID.fromString(idOf(station)), "CANCELLED", "UNSENT");

		var result = post(idOf(station), "deactivate", etagOf(station));

		assertThat(result).hasStatusOk();
	}

	@Test
	void aFinishedCardDoesNotBlockTheDeactivation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		aLiveCardAt(UUID.fromString(idOf(station)), "READY", "HANDED_OVER");

		var result = post(idOf(station), "deactivate", etagOf(station));

		assertThat(result).hasStatusOk();
	}

	@Test
	void aCategoryCannotBePlacedOnAnInactiveStation() {
		var station = createStation("Bar-" + UUID.randomUUID());
		post(idOf(station), "deactivate", etagOf(station));

		var result = createCategory(idOf(station));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-inactive");
	}

	@Test
	void aCategoryCannotBeMovedToAnInactiveStation() {
		var inactive = createStation("Bar-" + UUID.randomUUID());
		post(idOf(inactive), "deactivate", etagOf(inactive));
		var category = createCategory(null);

		var result = mvc.put()
			.uri(CATEGORIES + "/" + idOf(category))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(category))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Moved-%s\",\"stationId\":\"%s\",\"displayOrder\":0}".formatted(UUID.randomUUID(),
					idOf(inactive)))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-inactive");
	}

	@Test
	void aCategoryOnAnInactiveStationCannotBeReactivated() {
		var station = createStation("Bar-" + UUID.randomUUID());
		var category = createCategory(idOf(station));
		var inactiveCategory = deactivateCategory(idOf(category), etagOf(category));
		post(idOf(station), "deactivate", etagOf(station));

		var result = mvc.post()
			.uri(CATEGORIES + "/" + idOf(category) + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(inactiveCategory))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-inactive");
	}

	@Test
	void theFloorListLeavesOutInactiveStationsAndTheAdminListKeepsThem() {
		var station = createStation("Bar-" + UUID.randomUUID());
		post(idOf(station), "deactivate", etagOf(station));

		var floor = mvc.get().uri("/api/v1/stations").with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER)).exchange();
		var admin = mvc.get().uri(STATIONS).with(admin()).exchange();

		assertThat(floor).bodyJson().extractingPath("$[*].id").asList().doesNotContain(idOf(station));
		assertThat(admin).bodyJson().extractingPath("$[*].id").asList().contains(idOf(station));
	}

	@Test
	void aStaleOrMissingIfMatchIsRejected() {
		var station = createStation("Bar-" + UUID.randomUUID());

		assertThat(mvc.post().uri(STATIONS + "/" + idOf(station) + "/deactivate").with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(post(idOf(station), "deactivate", "\"9\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
	}

	@Test
	void reportsAnUnknownStation() {
		var result = post(UUID.randomUUID().toString(), "deactivate", "\"0\"");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-not-found");
	}

	@Test
	void onlyAnAdminMayChangeAStationStatus() {
		var station = createStation("Bar-" + UUID.randomUUID());

		var result = mvc.post()
			.uri(STATIONS + "/" + idOf(station) + "/deactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, etagOf(station))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private void aTicketLineAt(UUID stationId, String ticketStatus, String lineState) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		var ticket = UUID.randomUUID();
		jdbc.sql("""
				insert into instore.ticket (id, type, counter_name, status, currency, opened_by, created_at, updated_at)
				values (:id, 'COUNTER', 'Ana', :status, 'PEN', :user, :now, :now)
				""").param("id", ticket).param("status", ticketStatus).param("user", UUID.randomUUID()).param("now", now).update();
		jdbc.sql("""
				insert into instore.ticket_line (id, ticket_id, position, product_id, product_name, station_id, quantity,
				    unit_price_amount, unit_price_currency, state, board_source_id, created_at, updated_at)
				values (:id, :ticket, 0, :product, 'Juice', :station, 1, 5.00, 'PEN', :state,
				    case when :state = 'UNSENT' then null else :boardSource end, :now, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("ticket", ticket)
			.param("product", UUID.randomUUID())
			.param("station", stationId)
			.param("state", lineState)
			.param("boardSource", UUID.randomUUID())
			.param("now", now)
			.update();
	}

	private void aLiveCardAt(UUID stationId, String lineState, String orderStatus) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		var orderId = UUID.randomUUID();
		jdbc.sql("""
				insert into preparation.board_order (id, source, source_id, short_number, channel, status, sent_at,
				    created_at, updated_at)
				values (:id, 'TICKET', :source, 'T1', 'TABLE', :status, :now, :now, :now)
				""").param("id", orderId).param("source", UUID.randomUUID()).param("status", orderStatus).param("now", now).update();
		jdbc.sql("""
				insert into preparation.board_line (id, board_order_id, source_line_id, position, product_id, display_name,
				    quantity, modifier_signature, station_id, state, created_at, updated_at)
				values (:id, :order, :source, 0, :product, 'Juice', 1, 'sig', :station, :state, :now, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("order", orderId)
			.param("source", UUID.randomUUID())
			.param("product", UUID.randomUUID())
			.param("station", stationId)
			.param("state", lineState)
			.param("now", now)
			.update();
	}

	private MvcTestResult createStation(String name) {
		return mvc.post()
			.uri(STATIONS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();
	}

	private MvcTestResult createCategory(String stationId) {
		var station = stationId == null ? "" : ",\"stationId\":\"" + stationId + "\"";
		return mvc.post()
			.uri(CATEGORIES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0%s}".formatted(UUID.randomUUID(), station))
			.exchange();
	}

	private MvcTestResult deactivateCategory(String id, String etag) {
		return mvc.post()
			.uri(CATEGORIES + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.exchange();
	}

	private MvcTestResult post(String id, String action, String etag) {
		return mvc.post()
			.uri(STATIONS + "/" + id + "/" + action)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String etagOf(MvcTestResult result) {
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
