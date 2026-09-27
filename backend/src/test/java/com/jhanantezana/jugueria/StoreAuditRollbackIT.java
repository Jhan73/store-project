package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.internal.DeliveryZoneRepository;
import com.jhanantezana.jugueria.store.internal.ReasonRepository;
import com.jhanantezana.testsupport.AuthenticatedAs;

// Same guarantee as AuditRollbackIT, for every store command: audit's synchronous listener runs inside
// the publisher's transaction, so a failed audit write rolls back the store change too.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreAuditRollbackIT {

	static final String SETTINGS = "/api/v1/admin/settings";

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
		var etag = currentSettingsETag();

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

	private String currentSettingsETag() {
		var result = mvc.get().uri(SETTINGS).with(admin()).exchange();
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
