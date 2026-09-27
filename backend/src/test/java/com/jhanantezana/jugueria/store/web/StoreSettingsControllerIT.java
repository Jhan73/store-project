package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
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
import com.jhanantezana.testsupport.AuthenticatedAs;

// store_settings is a singleton row shared by the whole suite, so every test re-establishes a known
// baseline first (via GET+PUT) instead of assuming a fixed starting ETag or field values.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreSettingsControllerIT {

	static final String SETTINGS = "/api/v1/admin/settings";

	@Autowired
	MockMvcTester mvc;

	@BeforeEach
	void resetToKnownBaseline() {
		var etag = currentETag();
		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 5, 10, "20.00", 3, 20))
			.exchange();
		assertThat(result).hasStatusOk();
	}

	@Test
	void getReturnsCurrentSettingsWithAnETag() {
		var result = mvc.get().uri(SETTINGS).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.basePrepMinutes").isEqualTo(10);
		assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("PEN");
	}

	@Test
	void updatesSettingsAndReturnsANewETag() {
		var etag = currentETag();

		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(12, 3, 20, 6, 12, "30.00", 4, 25))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.basePrepMinutes").isEqualTo(12);
		assertThat(result).bodyJson().extractingPath("$.exceptionThreshold").isEqualTo(4);
		var newETag = result.getResponse().getHeader(HttpHeaders.ETAG);
		assertThat(newETag).isNotEqualTo(etag);
	}

	@Test
	void rejectsAnUpdateWithoutIfMatch() {
		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 5, 10, "20.00", 3, 20))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.precondition-required");
	}

	@Test
	void rejectsAnUpdateWithAMismatchedIfMatch() {
		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"999999\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 5, 10, "20.00", 3, 20))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.precondition-failed");
	}

	@Test
	void rejectsAnUpdateWithAMalformedIfMatch() {
		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "not-a-version")
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 5, 10, "20.00", 3, 20))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.malformed-request");
	}

	@Test
	void rejectsInvalidBoardThresholds() {
		var etag = currentETag();

		var result = mvc.put()
			.uri(SETTINGS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 10, 10, "20.00", 3, 20))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.invalid-board-thresholds");
	}

	@Test
	void rejectsGetWhenAnonymous() {
		var result = mvc.get().uri(SETTINGS).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsGetForANonAdminRole() {
		var result = mvc.get().uri(SETTINGS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsUpdateWhenAnonymous() {
		var result = mvc.put()
			.uri(SETTINGS)
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 5, 10, "20.00", 3, 20))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsUpdateForANonAdminRole() {
		var result = mvc.put()
			.uri(SETTINGS)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(settingsJson(10, 2, 15, 5, 10, "20.00", 3, 20))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Same valid ETag, only one request can win; the loser gets 412 or 409 depending on scheduling.
	@Test
	void exactlyOneOfTwoConcurrentUpdatesWithTheSameIfMatchWins() throws Exception {
		var etag = currentETag();
		var barrier = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<MvcTestResult>> futures = IntStream.range(0, 2)
				.mapToObj(i -> executor.submit(() -> {
					barrier.await();
					return mvc.put()
						.uri(SETTINGS)
						.with(admin())
						.header(HttpHeaders.IF_MATCH, etag)
						.contentType(MediaType.APPLICATION_JSON)
						.content(settingsJson(11 + i, 2, 15, 5, 10, "20.00", 3, 20))
						.exchange();
				}))
				.toList();

			var statuses = futures.stream().map(this::join).map(r -> r.getResponse().getStatus()).sorted().toList();

			assertThat(statuses.stream().filter(status -> status == HttpStatus.OK.value()).count()).isEqualTo(1);
			assertThat(statuses.stream()
				.filter(status -> status != HttpStatus.OK.value())
				.allMatch(status -> status == HttpStatus.CONFLICT.value()
						|| status == HttpStatus.PRECONDITION_FAILED.value())).isTrue();
		}
		finally {
			executor.shutdown();
		}
	}

	private MvcTestResult join(Future<MvcTestResult> future) {
		try {
			return future.get();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String currentETag() {
		var result = mvc.get().uri(SETTINGS).with(admin()).exchange();
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static String settingsJson(int basePrep, int queuePerOrder, int busyMinutes, int boardWarning,
			int boardLate, String registerThreshold, int exceptionThreshold, int onlineCapacity) {
		return """
				{
				  "timeZone": "America/Lima",
				  "currency": "PEN",
				  "basePrepMinutes": %d,
				  "queueMinutesPerOrder": %d,
				  "busyModeMinutes": %d,
				  "boardWarningMinutes": %d,
				  "boardLateMinutes": %d,
				  "registerDifferenceThreshold": { "amount": "%s", "currency": "PEN" },
				  "exceptionThreshold": %d,
				  "onlineCapacityLimit": %d
				}
				""".formatted(basePrep, queuePerOrder, busyMinutes, boardWarning, boardLate, registerThreshold,
				exceptionThreshold, onlineCapacity);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
