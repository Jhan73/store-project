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
import com.jhanantezana.jugueria.store.internal.ReasonRepository;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReasonControllerIT {

	static final String REASONS = "/api/v1/admin/reasons";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	ReasonRepository reasons;

	@AfterEach
	void cleanUp() {
		reasons.deleteAll();
	}

	@Test
	void createsAReason() {
		var code = "Customer changed mind-" + UUID.randomUUID();
		var result = create("VOID", code);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("VOID");
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
	}

	@Test
	void listsReasonsFilteredByType() {
		var voidCode = "Wrong order-" + UUID.randomUUID();
		var compCode = "Manager comp-" + UUID.randomUUID();
		create("VOID", voidCode);
		create("COMP", compCode);

		var result = mvc.get().uri(REASONS + "?type=VOID").with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + voidCode + "')]").asList().hasSize(1);
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + compCode + "')]").asList().isEmpty();
	}

	@Test
	void rejectsADuplicateCodeForTheSameType() {
		var code = "Duplicate-" + UUID.randomUUID();
		create("CASH_OUT", code);

		var result = create("CASH_OUT", code);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.reason-code-already-used");
	}

	@Test
	void allowsTheSameCodeForADifferentType() {
		var code = "Reused-" + UUID.randomUUID();
		create("VOID", code);

		var result = create("COMP", code);

		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	// Unlike a delivery zone name, a reason code stays reserved per type even once deactivated.
	@Test
	void aDeactivatedReasonCodeCannotBeReused() {
		var code = "Damaged-" + UUID.randomUUID();
		var created = create("STOCK_ADJUSTMENT", code);
		var id = idOf(created);
		mvc.post().uri(REASONS + "/" + id + "/deactivate").with(admin()).header(HttpHeaders.IF_MATCH, etagOf(created)).exchange();

		var result = create("STOCK_ADJUSTMENT", code);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("store.reason-code-already-used");
	}

	@Test
	void deactivatesAndReactivatesAReason() {
		var created = create("STOCK_ADJUSTMENT", "Damaged-" + UUID.randomUUID());
		var id = idOf(created);

		var deactivated = mvc.post()
			.uri(REASONS + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();
		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);

		var reactivated = mvc.post()
			.uri(REASONS + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(deactivated))
			.exchange();
		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void rejectsADeactivateWithoutIfMatch() {
		var created = create("VOID", "Reason-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post().uri(REASONS + "/" + id + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	// A and B both read the same reason; A deactivates first, then B's stale reactivate must not win the race.
	@Test
	void aStaleReactivateIsRejectedAndTheOtherAdminsChangeIsKept() {
		var created = create("VOID", "Reason-" + UUID.randomUUID());
		var id = idOf(created);
		var staleEtag = etagOf(created);

		var deactivated = mvc.post()
			.uri(REASONS + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, staleEtag)
			.exchange();
		assertThat(deactivated).hasStatusOk();

		var lateReactivate = mvc.post()
			.uri(REASONS + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, staleEtag)
			.exchange();
		assertThat(lateReactivate).hasStatus(HttpStatus.PRECONDITION_FAILED);

		var current = mvc.get().uri(REASONS + "?type=VOID").with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$[?(@.id=='" + id + "')].active").asList().containsExactly(false);
	}

	@Test
	void rejectsListWhenAnonymous() {
		var result = mvc.get().uri(REASONS + "?type=VOID").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsListForANonAdminRole() {
		var result = mvc.get()
			.uri(REASONS + "?type=VOID")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsCreateWhenAnonymous() {
		var result = mvc.post()
			.uri(REASONS)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"VOID\",\"code\":\"Reason-" + UUID.randomUUID() + "\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsCreateForANonAdminRole() {
		var result = mvc.post()
			.uri(REASONS)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"VOID\",\"code\":\"Reason-" + UUID.randomUUID() + "\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsDeactivateWhenAnonymous() {
		var created = create("VOID", "Reason-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post().uri(REASONS + "/" + id + "/deactivate").header(HttpHeaders.IF_MATCH, etagOf(created)).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsDeactivateForANonAdminRole() {
		var created = create("VOID", "Reason-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post()
			.uri(REASONS + "/" + id + "/deactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsReactivateWhenAnonymous() {
		var created = create("VOID", "Reason-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post().uri(REASONS + "/" + id + "/reactivate").header(HttpHeaders.IF_MATCH, etagOf(created)).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsReactivateForANonAdminRole() {
		var created = create("VOID", "Reason-" + UUID.randomUUID());
		var id = idOf(created);

		var result = mvc.post()
			.uri(REASONS + "/" + id + "/reactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String etagOf(MvcTestResult result) {
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private MvcTestResult create(String type, String code) {
		return mvc.post()
			.uri(REASONS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"%s\",\"code\":\"%s\"}".formatted(type, code))
			.exchange();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
