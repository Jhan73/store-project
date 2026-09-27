package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.DeliveryZone;
import com.jhanantezana.jugueria.store.internal.DeliveryZoneRepository;
import com.jhanantezana.jugueria.store.internal.Reason;
import com.jhanantezana.jugueria.store.internal.ReasonRepository;
import com.jhanantezana.testsupport.AuthenticatedAs;

// Same guarantee as AuditRollbackIT, for every one of the 7 store commands that publish an audited event.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreAuditRollbackIT {

	static final String SETTINGS = "/api/v1/admin/settings";

	static final String OPENING_HOURS = "/api/v1/admin/settings/opening-hours";

	static final String ZONES = "/api/v1/admin/delivery-zones";

	static final String REASONS = "/api/v1/admin/reasons";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	DeliveryZoneRepository zones;

	@Autowired
	ReasonRepository reasons;

	@MockitoBean
	AuditLogRepository auditLogs;

	@BeforeEach
	void auditWritesAlwaysFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	@AfterEach
	void cleanUp() {
		zones.deleteAll();
		reasons.deleteAll();
	}

	@Test
	void anAuditWriteFailureRollsBackASettingsUpdate() {
		var etag = currentETag(SETTINGS);

		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "timeZone": "America/Lima", "currency": "PEN", "basePrepMinutes": 99,
					  "queueMinutesPerOrder": 2, "busyModeMinutes": 15, "boardWarningMinutes": 5,
					  "boardLateMinutes": 10, "registerDifferenceThreshold": { "amount": "20.00", "currency": "PEN" },
					  "exceptionThreshold": 3, "onlineCapacityLimit": 20 }
					""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		var afterFailure = mvc.get().uri(SETTINGS).with(admin()).exchange();
		assertThat(afterFailure).bodyJson().extractingPath("$.basePrepMinutes").isNotEqualTo(99);
	}

	@Test
	void anAuditWriteFailureRollsBackAnOpeningHoursReplace() {
		var etag = currentETag(OPENING_HOURS);

		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					[
					  {"dayOfWeek":"MONDAY","closed":true,"opensAt":null,"closesAt":null},
					  {"dayOfWeek":"TUESDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
					  {"dayOfWeek":"WEDNESDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
					  {"dayOfWeek":"THURSDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
					  {"dayOfWeek":"FRIDAY","closed":false,"opensAt":"08:00:00","closesAt":"23:00:00"},
					  {"dayOfWeek":"SATURDAY","closed":false,"opensAt":"08:00:00","closesAt":"23:00:00"},
					  {"dayOfWeek":"SUNDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"}
					]
					""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		var afterFailure = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();
		assertThat(afterFailure).bodyJson().extractingPath("$[?(@.dayOfWeek=='MONDAY')].closed").asList()
			.containsExactly(false);
		assertThat(afterFailure.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(etag);
	}

	@Test
	void anAuditWriteFailureRollsBackADeliveryZoneCreation() {
		var name = "Rollback-" + UUID.randomUUID();

		var result = mvc.post()
			.uri(ZONES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "%s", "fee": { "amount": "5.00", "currency": "PEN" }, "deliveryMinutes": 20,
					  "minimumOrder": null, "freeDeliveryThreshold": null }
					""".formatted(name))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(zones.findAll()).noneMatch(zone -> zone.getName().equals(name));
	}

	@Test
	void anAuditWriteFailureRollsBackADeliveryZoneChange() {
		var name = "Rollback-" + UUID.randomUUID();
		var zone = zones.save(new DeliveryZone(name, Money.of("5.00", Currency.getInstance("PEN")), 20, null,
				null, Instant.now()));

		var result = mvc.patch()
			.uri(ZONES + "/" + zone.getId())
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "%s", "fee": { "amount": "9.00", "currency": "PEN" }, "deliveryMinutes": 30,
					  "minimumOrder": null, "freeDeliveryThreshold": null }
					""".formatted(name))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(zones.findById(zone.getId()).orElseThrow().getFee().amount().toPlainString()).isEqualTo("5.00");
	}

	@Test
	void anAuditWriteFailureRollsBackADeliveryZoneStatusChange() {
		var zone = zones.save(new DeliveryZone("Rollback-" + UUID.randomUUID(),
				Money.of("5.00", Currency.getInstance("PEN")), 20, null, null, Instant.now()));

		var result = mvc.post()
			.uri(ZONES + "/" + zone.getId() + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(zones.findById(zone.getId()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAReasonCreation() {
		var code = "Rollback-" + UUID.randomUUID();

		var result = mvc.post()
			.uri(REASONS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"VOID\",\"code\":\"%s\"}".formatted(code))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(reasons.findAll()).noneMatch(reason -> reason.getCode().equals(code));
	}

	@Test
	void anAuditWriteFailureRollsBackAReasonStatusChange() {
		var reason = reasons.save(new Reason(ReasonType.VOID, "Rollback-" + UUID.randomUUID(), Instant.now()));

		var result = mvc.post()
			.uri(REASONS + "/" + reason.getId() + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(reasons.findById(reason.getId()).orElseThrow().isActive()).isTrue();
	}

	private String currentETag(String uri) {
		var result = mvc.get().uri(uri).with(admin()).exchange();
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
