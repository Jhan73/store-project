package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("VOID");
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
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

	@Test
	void deactivatesAndReactivatesAReason() {
		var created = create("STOCK_ADJUSTMENT", "Damaged-" + UUID.randomUUID());
		var id = assertThat(created).bodyJson().extractingPath("$.id").actual().toString();

		var deactivated = mvc.post().uri(REASONS + "/" + id + "/deactivate").with(admin()).exchange();
		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);

		var reactivated = mvc.post().uri(REASONS + "/" + id + "/reactivate").with(admin()).exchange();
		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void rejectsWhenAnonymous() {
		var result = mvc.get().uri(REASONS + "?type=VOID").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsForANonAdminRole() {
		var result = mvc.get()
			.uri(REASONS + "?type=VOID")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
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
