package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

// The ticket editor matches the board's lines to its own through the line they came from.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketLineOnBoardIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID ticket;

	UUID line;

	@BeforeEach
	void aSentTicketLine() {
		var juice = InstoreTables.aSizedProduct(jdbc);
		ticket = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(floor())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange()));
		var added = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(floor())
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), juice.small()))
			.exchange();
		line = UUID.fromString(assertThat(added).bodyJson().extractingPath("$.lines[0].id").actual().toString());
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(floor()).exchange()).hasStatusOk();
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void theTicketsViewNamesTheTicketLineEachBoardLineCameFrom() {
		var result = mvc.get().uri("/api/v1/board/tickets").with(floor()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$[*].lines[?(@.sourceLineId=='" + line + "')]")
			.asList()
			.hasSize(1);
		assertThat(result).bodyJson()
			.extractingPath("$[*].lines[?(@.sourceLineId=='" + line + "' && @.id=='" + line + "')]")
			.asList()
			.isEmpty();
	}

	@Test
	void theReadyViewNamesItToo() {
		jdbc.sql("update preparation.board_line set state = 'READY', ready_at = now() where source_line_id = ?")
			.param(line)
			.update();
		jdbc.sql("update preparation.board_order set status = 'READY', ready_at = now() where source_id = ?")
			.param(ticket)
			.update();

		var result = mvc.get().uri("/api/v1/board/ready").with(floor()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$[*].lines[?(@.sourceLineId=='" + line + "' && @.state=='READY')]")
			.asList()
			.hasSize(1);
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static RequestPostProcessor floor() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER);
	}

}
