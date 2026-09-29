package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AllergenControllerIT {

	static final String ALLERGENS = "/api/v1/admin/allergens";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void theEnumMirrorsTheSeededTable() {
		var seeded = jdbc.sql("select code from catalog.allergen").query(String.class).list();

		assertThat(seeded).containsExactlyInAnyOrderElementsOf(Arrays.stream(Allergen.values()).map(Enum::name).toList());
	}

	@Test
	void listsTheFixedAllergens() {
		var result = mvc.get().uri(ALLERGENS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$").asList().contains("PEANUTS", "GLUTEN", "MILK");
		assertThat(result).bodyJson().extractingPath("$").asList().hasSize(Allergen.values().length);
	}

	@Test
	void rejectsAnonymousCallers() {
		assertThat(mvc.get().uri(ALLERGENS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsNonAdminRoles() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			assertThat(mvc.get().uri(ALLERGENS).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		}
	}

}
