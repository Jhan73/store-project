package com.jhanantezana.jugueria.store.web;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

/** The store currency cannot move under work in progress, and the time zone must be a region. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreSettingsGuardsIT {

	static final String SETTINGS = "/api/v1/admin/settings";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void restoreBaseline() {
		InstoreTables.clean(jdbc);
		put("America/Lima", "PEN");
	}

	@Test
	void refusesACurrencyChangeWhileAShiftIsOpen() {
		openShift();

		var result = put("America/Lima", "USD");

		assertProblem(result, HttpStatus.CONFLICT, "store.currency-locked");
		assertThat(currency()).isEqualTo("PEN");
	}

	@Test
	void refusesACurrencyChangeWhileATicketIsOpen() {
		openCounterTicket();

		assertProblem(put("America/Lima", "USD"), HttpStatus.CONFLICT, "store.currency-locked");
		assertThat(currency()).isEqualTo("PEN");
	}

	@Test
	void allowsACurrencyChangeWhenNothingIsOpen() {
		var result = put("America/Lima", "USD");

		assertThat(result).hasStatusOk();
		assertThat(currency()).isEqualTo("USD");
	}

	@Test
	void otherSettingsStillChangeWhileAShiftIsOpen() {
		openShift();
		openCounterTicket();

		assertThat(put("America/Bogota", "PEN")).hasStatusOk();
	}

	@Test
	void rejectsAFixedOffsetAsTheTimeZone() {
		for (var offset : new String[] { "+05:00", "-05:00", "UTC+5", "GMT-05:00", "Z" }) {
			assertProblem(put(offset, "PEN"), HttpStatus.UNPROCESSABLE_CONTENT, "store.invalid-settings-value");
		}
		assertThat(mvc.get().uri(SETTINGS).with(admin()).exchange()).bodyJson()
			.extractingPath("$.timeZone")
			.isEqualTo("America/Lima");
	}

	@Test
	void acceptsARegionZone() {
		assertThat(put("Europe/Madrid", "PEN")).hasStatusOk();
	}

	private void openShift() {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.sql("""
				insert into instore.register_shift (id, cashier_id, status, currency, float_amount, opened_at, updated_at)
				values (?, ?, 'OPEN', 'PEN', 10.00, ?, ?)
				""").param(UUID.randomUUID()).param(UUID.randomUUID()).param(now).param(now).update();
	}

	private void openCounterTicket() {
		jdbc.sql("""
				insert into instore.ticket (id, type, counter_name, status, currency, opened_by, created_at, updated_at)
				values (?, 'COUNTER', 'Ana', 'OPEN', 'PEN', ?, now(), now())
				""").param(UUID.randomUUID()).param(UUID.randomUUID()).update();
	}

	private String currency() {
		return jdbc.sql("select currency from store.store_settings").query(String.class).single().trim();
	}

	private MvcTestResult put(String timeZone, String currency) {
		var etag = mvc.get().uri(SETTINGS).with(admin()).exchange().getResponse().getHeader(HttpHeaders.ETAG);
		return mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "timeZone": "%s",
					  "currency": "%s",
					  "basePrepMinutes": 10,
					  "queueMinutesPerOrder": 2,
					  "busyModeMinutes": 15,
					  "boardWarningMinutes": 5,
					  "boardLateMinutes": 10,
					  "registerDifferenceThreshold": { "amount": "20.00", "currency": "%s" },
					  "exceptionThreshold": 3,
					  "onlineCapacityLimit": 20,
					  "allowNegativeStockOnSale": false
					}
					""".formatted(timeZone, currency, currency))
			.exchange();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
