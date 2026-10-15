package com.jhanantezana.jugueria.preparation.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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
import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.BoardOrderIntake;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.preparation.internal.BoardCommands;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.PreparationTables;

import org.assertj.core.api.InstanceOfAssertFactories;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BoardDisplayControllerIT {

	static final String DISPLAY = "/api/v1/board/display";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PreparationApi preparation;

	@Autowired
	BoardCommands commands;

	@AfterEach
	void cleanUp() {
		PreparationTables.clean(jdbc);
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "SERVER", "CASHIER", "ADMIN" })
	void staffRolesMayReadTheDisplay(Role role) {
		var result = mvc.get().uri(DISPLAY).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange();

		assertThat(result).hasStatusOk();
	}

	@Test
	void aCustomerMayNotReadTheDisplay() {
		var result = mvc.get().uri(DISPLAY).with(AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
	}

	@Test
	void anAnonymousCallerMayNotReadTheDisplay() {
		assertThat(mvc.get().uri(DISPLAY).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void splitsTheCardsIntoPreparingAndReadyWithTheirNumberAndFirstName() {
		accept("L-001", BoardChannel.COUNTER, "Ana Perez");
		var ready = accept("L-002", BoardChannel.COUNTER, "Beto");
		commands.start(ready);
		commands.readyOrder(ready);
		accept("L-003", BoardChannel.TABLE, "Table 4");

		var result = mvc.get().uri(DISPLAY).with(server()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.preparing[0].shortNumber").isEqualTo("L-001");
		assertThat(result).bodyJson().extractingPath("$.preparing[0].firstName").isEqualTo("Ana");
		assertThat(result).bodyJson().extractingPath("$.preparing[1].shortNumber").isEqualTo("L-003");
		assertThat(result).bodyJson().extractingPath("$.preparing[1].firstName").isNull();
		assertThat(result).bodyJson().extractingPath("$.ready[0].shortNumber").isEqualTo("L-002");
		assertThat(result).bodyJson().extractingPath("$.ready[0].firstName").isEqualTo("Beto");
	}

	@Test
	void exposesOnlyTheNumberAndTheFirstNameOfEachCard() throws Exception {
		var allergy = new BoardLineIntake(UUID.randomUUID(), UUID.randomUUID(), "Mango smoothie", 2, "large",
				List.of(UUID.randomUUID()), "no peanuts, severe allergy", true, UUID.randomUUID());
		var intake = new BoardOrderIntake(BoardSource.QUICK_SALE, UUID.randomUUID(), "Q-009", BoardChannel.COUNTER,
				"Ana Perez Soto", Instant.now(), null, List.of(allergy));
		var order = preparation.accept(intake);

		var result = mvc.get().uri(DISPLAY).with(server()).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$")
			.asInstanceOf(InstanceOfAssertFactories.MAP)
			.containsOnlyKeys("preparing", "ready");
		assertThat(result).bodyJson()
			.extractingPath("$.preparing[0]")
			.asInstanceOf(InstanceOfAssertFactories.MAP)
			.containsOnlyKeys("shortNumber", "firstName");
		var body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("Perez", "Soto", "Mango", "peanuts", "allergy", "large", order.toString(),
				allergy.stationId().toString(), allergy.productId().toString(), intake.sourceId().toString());
	}

	@Test
	void showsNothingForHandedOverAndCancelledCards() {
		var handed = accept("L-001", BoardChannel.COUNTER, "Ana");
		commands.start(handed);
		commands.readyOrder(handed);
		commands.handover(handed);

		var result = mvc.get().uri(DISPLAY).with(server()).exchange();

		assertThat(result).bodyJson().extractingPath("$.preparing.length()").isEqualTo(0);
		assertThat(result).bodyJson().extractingPath("$.ready.length()").isEqualTo(0);
	}

	private UUID accept(String shortNumber, BoardChannel channel, String label) {
		return preparation.accept(new BoardOrderIntake(BoardSource.TICKET, UUID.randomUUID(), shortNumber, channel, label,
				Instant.now(), null, List.of(PreparationTables.aLine())));
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor server() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER);
	}

}
