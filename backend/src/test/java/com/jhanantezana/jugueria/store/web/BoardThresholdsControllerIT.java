package com.jhanantezana.jugueria.store.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardThresholdsControllerIT {

	static final String THRESHOLDS = "/api/v1/store/board-thresholds";

	static final String SETTINGS = "/api/v1/admin/settings";

	@Autowired
	MockMvcTester mvc;

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "SERVER", "CASHIER", "ADMIN" })
	void returnsTheStoredThresholdsForEveryStaffRole(Role role) {
		var settings = mvc.get().uri(SETTINGS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN)).exchange();
		var warning = assertThat(settings).bodyJson().extractingPath("$.boardWarningMinutes").actual();
		var late = assertThat(settings).bodyJson().extractingPath("$.boardLateMinutes").actual();

		var result = mvc.get().uri(THRESHOLDS).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.boardWarningMinutes").isEqualTo(warning);
		assertThat(result).bodyJson().extractingPath("$.boardLateMinutes").isEqualTo(late);
	}

	@Test
	void exposesOnlyTheTwoThresholds() {
		var result = mvc.get().uri(THRESHOLDS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER)).exchange();

		assertThat(result).bodyJson().extractingPath("$").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
			.containsOnlyKeys("boardWarningMinutes", "boardLateMinutes");
	}

	@Test
	void aCustomerMayNotReadThem() {
		var result = mvc.get().uri(THRESHOLDS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@Test
	void rejectsReadWhenAnonymous() {
		var result = mvc.get().uri(THRESHOLDS).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void theAdminSettingsStayAdminOnly() {
		var result = mvc.get().uri(SETTINGS).with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

}
