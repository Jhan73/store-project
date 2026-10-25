package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.DeliveryZoneDeleted;
import com.jhanantezana.jugueria.store.DeliveryZoneUsageGuard;
import com.jhanantezana.jugueria.store.internal.DeliveryZoneRepository;
import com.jhanantezana.testsupport.AuthenticatedAs;

// The ordering module does not exist yet, so a guard that says "in use" stands in for the one it will add.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, DeliveryZoneDeleteIT.OrderingStandIn.class })
@RecordApplicationEvents
class DeliveryZoneDeleteIT {

	static final String ZONES = "/api/v1/admin/delivery-zones";

	static final Set<UUID> ZONES_IN_USE = ConcurrentHashMap.newKeySet();

	@TestConfiguration
	static class OrderingStandIn {

		@Bean
		DeliveryZoneUsageGuard orderedZones() {
			return zoneIds -> zoneIds.stream().filter(ZONES_IN_USE::contains).collect(Collectors.toSet());
		}

	}

	@Autowired
	MockMvcTester mvc;

	@Autowired
	DeliveryZoneRepository zones;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		ZONES_IN_USE.clear();
		zones.deleteAll();
	}

	@Test
	void deletesAnUnusedZoneAndPublishesItsSnapshot() {
		var created = create();

		var result = delete(idOf(created), etagOf(created));

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(zones.count()).isZero();
		assertThat(events.stream(DeliveryZoneDeleted.class)).singleElement().satisfies(event -> {
			assertThat(event.zoneId()).isEqualTo(UUID.fromString(idOf(created)));
			assertThat(event.before().name()).startsWith("Zone-");
		});
	}

	@Test
	void aRefusingGuardBlocksTheDelete() {
		var created = create();
		ZONES_IN_USE.add(UUID.fromString(idOf(created)));

		var result = delete(idOf(created), etagOf(created));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.delivery-zone-in-use");
		assertThat(zones.count()).isEqualTo(1);
		assertThat(events.stream(DeliveryZoneDeleted.class)).isEmpty();
	}

	@Test
	void theListSaysWhichZonesCanBeDeleted() {
		var free = create();
		var used = create();
		ZONES_IN_USE.add(UUID.fromString(idOf(used)));

		var result = mvc.get().uri(ZONES).with(admin()).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='%s')].deletable".formatted(idOf(free)))
			.asList()
			.containsExactly(true);
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='%s')].deletable".formatted(idOf(used)))
			.asList()
			.containsExactly(false);
	}

	@Test
	void aNewZoneSaysItCanBeDeleted() {
		assertThat(create()).bodyJson().extractingPath("$.deletable").isEqualTo(true);
	}

	@Test
	void aStaleOrMissingIfMatchIsRejectedAndAnUnknownZoneIsNotFound() {
		var created = create();

		assertThat(mvc.delete().uri(ZONES + "/" + idOf(created)).with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(delete(idOf(created), "\"9\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		var unknown = delete(UUID.randomUUID().toString(), "\"0\"");
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("store.delivery-zone-not-found");
		assertThat(zones.count()).isEqualTo(1);
	}

	@Test
	void onlyAnAdminMayDelete() {
		var created = create();

		var result = mvc.delete()
			.uri(ZONES + "/" + idOf(created))
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult create() {
		return mvc.post().uri(ZONES).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Zone-%s", "fee": { "amount": "5.00", "currency": "PEN" }, "deliveryMinutes": 20 }
				""".formatted(UUID.randomUUID())).exchange();
	}

	private MvcTestResult delete(String id, String etag) {
		return mvc.delete().uri(ZONES + "/" + id).with(admin()).header(HttpHeaders.IF_MATCH, etag).exchange();
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
