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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

// The 7 rows are seeded once by migration and shared by the whole suite: every test restores the
// standard week afterwards instead of assuming a fixed starting state.
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
			.contentType(MediaType.APPLICATION_JSON)
			.content(standardWeekJson())
			.exchange();
	}

	@Test
	void listsAllSevenDays() {
		var result = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(7);
	}

	@Test
	void replacesTheWholeWeek() {
		var result = mvc.put()
			.uri(OPENING_HOURS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(weekJsonWithSundayClosed())
			.exchange();

		assertThat(result).hasStatusOk();
		var sunday = mvc.get().uri(OPENING_HOURS).with(admin()).exchange();
		assertThat(sunday).bodyJson()
			.extractingPath("$[?(@.dayOfWeek=='SUNDAY')].closed")
			.asList()
			.containsExactly(true);
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
			.contentType(MediaType.APPLICATION_JSON)
			.content(missingSunday)
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
			.contentType(MediaType.APPLICATION_JSON)
			.content(invalidMonday)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.invalid-opening-hours");
	}

	@Test
	void rejectsWhenAnonymous() {
		var result = mvc.get().uri(OPENING_HOURS).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsForANonAdminRole() {
		var result = mvc.get()
			.uri(OPENING_HOURS)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
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
