package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.internal.DeliveryZoneRepository;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeliveryZoneControllerIT {

	static final String ZONES = "/api/v1/admin/delivery-zones";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	DeliveryZoneRepository zones;

	@AfterEach
	void cleanUp() {
		zones.deleteAll();
	}

	@Test
	void createsAZone() {
		var name = "Downtown-" + UUID.randomUUID();
		var result = create(name);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying("Location", values -> assertThat(values).singleElement());
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(name);
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
	}

	@Test
	void rejectsANegativeFee() {
		var name = "Zone-" + UUID.randomUUID();
		var result = mvc.post()
			.uri(ZONES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson(name, "-1.00", 20, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.invalid-delivery-zone");
	}

	@Test
	void rejectsACurrencyMismatch() {
		var name = "Zone-" + UUID.randomUUID();
		var result = mvc.post()
			.uri(ZONES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "%s", "fee": { "amount": "5.00", "currency": "USD" }, "deliveryMinutes": 20,
					  "minimumOrder": null, "freeDeliveryThreshold": null }
					""".formatted(name))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.delivery-zone-currency-mismatch");
	}

	@Test
	void rejectsADuplicateActiveName() {
		var name = "Duplicate-" + UUID.randomUUID();
		create(name);

		var result = mvc.post()
			.uri(ZONES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson(name, "5.00", 20, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.delivery-zone-name-already-used");
	}

	@Test
	void updatesAZone() {
		var name = "Zone-" + UUID.randomUUID();
		var created = create(name);
		var id = idOf(created);

		var result = change(id, etagOf(created), name, "9.00", 30, "20.00", "60.00");

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.fee.amount").isEqualTo("9.00");
		assertThat(result).bodyJson().extractingPath("$.deliveryMinutes").isEqualTo(30);
	}

	@Test
	void rejectsAChangeWithoutIfMatch() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.patch()
			.uri(ZONES + "/" + id)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson("Zone-" + UUID.randomUUID(), "9.00", 30, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void rejectsAChangeWithAMismatchedIfMatch() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = change(id, "\"999999\"", "New name", "9.00", 30, null, null);

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
	}

	// A and B both read the same zone; A writes first, then B's write with the stale ETag must not overwrite A's change.
	@Test
	void aStaleChangeIsRejectedAndTheOtherAdminsChangeIsKept() {
		var name = "Zone-" + UUID.randomUUID();
		var created = create(name);
		var id = idOf(created);
		var staleEtag = etagOf(created);

		var winner = change(id, staleEtag, name, "9.00", 30, null, null);
		assertThat(winner).hasStatusOk();

		var loser = change(id, staleEtag, name, "50.00", 45, null, null);
		assertThat(loser).hasStatus(HttpStatus.PRECONDITION_FAILED);

		var current = mvc.get().uri(ZONES).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$[?(@.id=='" + id + "')].fee.amount").asList()
			.containsExactly("9.00");
	}

	@Test
	void deactivatesAndReactivatesAZone() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var deactivated = mvc.post()
			.uri(ZONES + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();
		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);

		var reactivated = mvc.post()
			.uri(ZONES + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(deactivated))
			.exchange();
		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void rejectsADeactivateWithoutIfMatch() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post().uri(ZONES + "/" + id + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void rejectsADeactivateWithAStaleIfMatch() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);
		var staleEtag = etagOf(created);
		mvc.post().uri(ZONES + "/" + id + "/deactivate").with(admin()).header(HttpHeaders.IF_MATCH, staleEtag).exchange();

		var result = mvc.post()
			.uri(ZONES + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, staleEtag)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
	}

	@Test
	void aDeactivatedZoneFreesItsNameForReuse() {
		var name = "Reusable-" + UUID.randomUUID();
		var created = create(name);
		var id = idOf(created);
		mvc.post().uri(ZONES + "/" + id + "/deactivate").with(admin()).header(HttpHeaders.IF_MATCH, etagOf(created)).exchange();

		var result = create(name);

		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void rejectsListWhenAnonymous() {
		var result = mvc.get().uri(ZONES).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsListForANonAdminRole() {
		var result = mvc.get().uri(ZONES).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsCreateWhenAnonymous() {
		var result = mvc.post()
			.uri(ZONES)
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson("Zone-" + UUID.randomUUID(), "5.00", 20, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsCreateForANonAdminRole() {
		var result = mvc.post()
			.uri(ZONES)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson("Zone-" + UUID.randomUUID(), "5.00", 20, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsChangeWhenAnonymous() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.patch()
			.uri(ZONES + "/" + id)
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson("Zone-" + UUID.randomUUID(), "5.00", 20, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsChangeForANonAdminRole() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.patch()
			.uri(ZONES + "/" + id)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson("Zone-" + UUID.randomUUID(), "5.00", 20, null, null))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsDeactivateWhenAnonymous() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post().uri(ZONES + "/" + id + "/deactivate").header(HttpHeaders.IF_MATCH, etagOf(created)).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsDeactivateForANonAdminRole() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post()
			.uri(ZONES + "/" + id + "/deactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsReactivateWhenAnonymous() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post().uri(ZONES + "/" + id + "/reactivate").header(HttpHeaders.IF_MATCH, etagOf(created)).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsReactivateForANonAdminRole() {
		var created = create("Zone-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post()
			.uri(ZONES + "/" + id + "/reactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult change(String id, String ifMatch, String name, String fee, int deliveryMinutes,
			String minimumOrder, String freeThreshold) {
		return mvc.patch()
			.uri(ZONES + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, ifMatch)
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson(name, fee, deliveryMinutes, minimumOrder, freeThreshold))
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String etagOf(MvcTestResult result) {
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private MvcTestResult create(String name) {
		return mvc.post()
			.uri(ZONES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(zoneJson(name, "5.00", 20, null, null))
			.exchange();
	}

	private static String zoneJson(String name, String fee, int deliveryMinutes, String minimumOrder,
			String freeThreshold) {
		var minimum = minimumOrder == null ? "null" : "{\"amount\":\"%s\",\"currency\":\"PEN\"}".formatted(minimumOrder);
		var free = freeThreshold == null ? "null" : "{\"amount\":\"%s\",\"currency\":\"PEN\"}".formatted(freeThreshold);
		return """
				{
				  "name": "%s",
				  "fee": { "amount": "%s", "currency": "PEN" },
				  "deliveryMinutes": %d,
				  "minimumOrder": %s,
				  "freeDeliveryThreshold": %s
				}
				""".formatted(name, fee, deliveryMinutes, minimum, free);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
