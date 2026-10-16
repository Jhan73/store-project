package com.jhanantezana.jugueria.catalog.web;

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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FloorStationControllerIT {

	static final String STATIONS = "/api/v1/stations";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "SERVER", "CASHIER", "ADMIN" })
	void listsTheStationsForEveryStaffRole(Role role) {
		var result = mvc.get().uri(STATIONS).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[?(@.name=='Main')]").asList().hasSize(1);
	}

	@Test
	void exposesOnlyTheIdAndTheName() {
		var result = mvc.get().uri(STATIONS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER)).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$[0]")
			.asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
			.containsOnlyKeys("id", "name");
	}

	@Test
	void listsTheStationsByName() {
		jdbc.sql("insert into catalog.station (id, name, default_station, created_at, updated_at, version) "
				+ "values (?, 'Aaa-bar', false, now(), now(), 0)").param(UUID.randomUUID()).update();

		var result = mvc.get().uri(STATIONS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER)).exchange();

		assertThat(result).bodyJson().extractingPath("$[0].name").isEqualTo("Aaa-bar");
	}

	@Test
	void aCustomerMayNotReadThem() {
		var result = mvc.get().uri(STATIONS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@Test
	void rejectsReadWhenAnonymous() {
		assertThat(mvc.get().uri(STATIONS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void theAdminListStaysAdminOnly() {
		var result = mvc.get()
			.uri("/api/v1/admin/stations")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

}
