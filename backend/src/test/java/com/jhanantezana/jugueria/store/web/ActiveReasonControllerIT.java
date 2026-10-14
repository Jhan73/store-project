package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.ReasonRepository;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActiveReasonControllerIT {

	static final String ADMIN_REASONS = "/api/v1/admin/reasons";

	static final String REASONS = "/api/v1/reasons";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	ReasonRepository reasons;

	@AfterEach
	void cleanUp() {
		reasons.deleteAll();
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "SERVER", "CASHIER", "ADMIN" })
	void listsTheActiveReasonsOfATypeForEveryStaffRole(Role role) {
		var code = "Wrong order-" + UUID.randomUUID();
		create("VOID", code);

		var result = mvc.get().uri(REASONS + "?type=VOID").with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + code + "')]").asList().hasSize(1);
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + code + "')].type").asList().containsExactly("VOID");
	}

	@Test
	void doesNotListInactiveReasonsOrOtherTypes() {
		var inactive = "Retired-" + UUID.randomUUID();
		var comp = "Comp only-" + UUID.randomUUID();
		var created = create("VOID", inactive);
		create("COMP", comp);
		mvc.post()
			.uri(ADMIN_REASONS + "/" + idOf(created) + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, created.getResponse().getHeader(HttpHeaders.ETAG))
			.exchange();

		var result = mvc.get().uri(REASONS + "?type=VOID").with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + inactive + "')]").asList().isEmpty();
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + comp + "')]").asList().isEmpty();
	}

	@Test
	void exposesOnlyTheTypeAndTheCode() {
		var code = "Shape-" + UUID.randomUUID();
		create("CASH_OUT", code);

		var result = mvc.get().uri(REASONS + "?type=CASH_OUT").with(admin()).exchange();

		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + code + "')].id").asList().isEmpty();
		assertThat(result).bodyJson().extractingPath("$[?(@.code=='" + code + "')].etag").asList().isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = ReasonType.class, names = { "COMP", "CASH_OUT", "STOCK_ADJUSTMENT" })
	void aServerMayOnlyReadTheVoidReasons(ReasonType type) {
		var result = mvc.get()
			.uri(REASONS + "?type=" + type)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@ParameterizedTest
	@EnumSource(ReasonType.class)
	void aCashierMayReadEveryType(ReasonType type) {
		var result = mvc.get()
			.uri(REASONS + "?type=" + type)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.exchange();

		assertThat(result).hasStatusOk();
	}

	@ParameterizedTest
	@EnumSource(ReasonType.class)
	void aCustomerMayNotReadAnyType(ReasonType type) {
		var result = mvc.get()
			.uri(REASONS + "?type=" + type)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@Test
	void rejectsAMissingType() {
		var result = mvc.get().uri(REASONS).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsListWhenAnonymous() {
		var result = mvc.get().uri(REASONS + "?type=VOID").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void theAdminListStaysAdminOnly() {
		var result = mvc.get()
			.uri(ADMIN_REASONS + "?type=VOID")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private MvcTestResult create(String type, String code) {
		return mvc.post()
			.uri(ADMIN_REASONS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"%s\",\"code\":\"%s\"}".formatted(type, code))
			.exchange();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
