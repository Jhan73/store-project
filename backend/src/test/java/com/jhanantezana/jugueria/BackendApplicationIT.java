package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BackendApplicationIT {

	@Autowired
	MockMvcTester mvc;

	@Test
	void reportsReadinessWithoutAuthentication() {
		assertThat(mvc.get().uri("/actuator/health/readiness"))
			.hasStatusOk()
			.bodyJson().extractingPath("$.status").isEqualTo("UP");
	}

	@Test
	void reportsLivenessWithoutAuthentication() {
		assertThat(mvc.get().uri("/actuator/health/liveness"))
			.hasStatusOk()
			.bodyJson().extractingPath("$.status").isEqualTo("UP");
	}

	@Test
	void deniesEverythingOutsideTheHealthEndpoints() {
		assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/v1/anything")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void packagesMigrationsFromTheRepositoryRootOnTheClasspath() {
		assertThat(new ClassPathResource("db/migration").exists()).isTrue();
	}

}
