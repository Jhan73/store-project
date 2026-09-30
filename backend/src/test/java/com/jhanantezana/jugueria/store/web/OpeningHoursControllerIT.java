package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
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
import com.jhanantezana.testsupport.AuthenticatedAs;

// The 7 rows and their counter are shared by the whole suite: every test restores the standard week afterwards.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpeningHoursControllerIT {

	static final String OPENING_HOURS = "/api/v1/admin/settings/opening-hours";

	@Autowired
	MockMvcTester mvc;

	@AfterEach
	void restoreStandardWeek() {
		mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();
	}

	@Test
	void listsAllSevenDays() {
		var result = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(7);
	}

	@Test
	void replacesTheWholeWeek() {
		var etag = currentETag();

		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(weekJsonWithSundayClosed())
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(etag);
		var sunday = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();
		assertThat(sunday).bodyJson()
			.extractingPath("$[?(@.dayOfWeek=='SUNDAY')].closed")
			.asList()
			.containsExactly(true);
	}

	@Test
	void rejectsAPutWithoutIfMatch() {
		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void rejectsAPutWithAMismatchedIfMatch() {
		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"999999\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
	}

	// A and B both read the current week; A writes first, then B's stale write must not overwrite A's change.
	@Test
	void aStalePutIsRejectedAndTheOtherAdminsChangeIsKept() {
		var staleEtag = currentETag();

		var winner = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, staleEtag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(weekJsonWithSundayClosed())
			.exchange();
		assertThat(winner).hasStatusOk();

		var loser = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, staleEtag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();
		assertThat(loser).hasStatus(HttpStatus.PRECONDITION_FAILED);

		var current = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$[?(@.dayOfWeek=='SUNDAY')].closed").asList().containsExactly(true);
	}

	@Test
	void rejectsAWeekMissingADay() {
		var missingSunday = """
				[
				  {"dayOfWeek":"MONDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
				  {"dayOfWeek":"TUESDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
				  {"dayOfWeek":"WEDNESDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
				  {"dayOfWeek":"THURSDAY","closed":false,"opensAt":"08:00:00","closesAt":"22:00:00"},
				  {"dayOfWeek":"FRIDAY","closed":false,"opensAt":"08:00:00","closesAt":"23:00:00"},
				  {"dayOfWeek":"SATURDAY","closed":false,"opensAt":"08:00:00","closesAt":"23:00:00"}
				]
				""";

		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(missingSunday)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.invalid-opening-hours");
	}

	@Test
	void rejectsAWeekWithADuplicateDay() {
		var duplicateMonday = standardWeekJson().replace(
				"{\"dayOfWeek\":\"SUNDAY\",\"closed\":false,\"opensAt\":\"08:00:00\",\"closesAt\":\"22:00:00\"}",
				"{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":\"09:00:00\",\"closesAt\":\"21:00:00\"}");

		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(duplicateMonday)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.invalid-opening-hours");
	}

	@Test
	void rejectsAnOpenDayMissingTimes() {
		var invalidMonday = standardWeekJson().replace(
				"{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":\"08:00:00\",\"closesAt\":\"22:00:00\"}",
				"{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"opensAt\":null,\"closesAt\":null}");

		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(invalidMonday)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.invalid-opening-hours");
	}

	@Test
	void rejectsListWhenAnonymous() {
		var result = mvc.get().uri(OPENING_HOURS).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsListForANonAdminRole() {
		var result = mvc.get()
			.uri(OPENING_HOURS)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsPutWhenAnonymous() {
		var result = mvc.put()
			.uri(OPENING_HOURS)
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsPutForANonAdminRole() {
		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header(HttpHeaders.IF_MATCH, currentETag())
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private String currentETag() {
		MvcTestResult result = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static String weekJsonWithSundayClosed() {
		return standardWeekJson().replace(
				"{\"dayOfWeek\":\"SUNDAY\",\"closed\":false,\"opensAt\":\"08:00:00\",\"closesAt\":\"22:00:00\"}",
				"{\"dayOfWeek\":\"SUNDAY\",\"closed\":true,\"opensAt\":null,\"closesAt\":null}");
	}

	private static String standardWeekJson() {
		return jsonOf(dayJson(DayOfWeek.MONDAY, "08:00:00", "22:00:00"), dayJson(DayOfWeek.TUESDAY, "08:00:00", "22:00:00"),
				dayJson(DayOfWeek.WEDNESDAY, "08:00:00", "22:00:00"), dayJson(DayOfWeek.THURSDAY, "08:00:00", "22:00:00"),
				dayJson(DayOfWeek.FRIDAY, "08:00:00", "23:00:00"), dayJson(DayOfWeek.SATURDAY, "08:00:00", "23:00:00"),
				dayJson(DayOfWeek.SUNDAY, "08:00:00", "22:00:00"));
	}

	private static String jsonOf(String... days) {
		return "[" + String.join(",", days) + "]";
	}

	private static String dayJson(DayOfWeek day, String opensAt, String closesAt) {
		return "{\"dayOfWeek\":\"%s\",\"closed\":false,\"opensAt\":\"%s\",\"closesAt\":\"%s\"}".formatted(day, opensAt,
				closesAt);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
