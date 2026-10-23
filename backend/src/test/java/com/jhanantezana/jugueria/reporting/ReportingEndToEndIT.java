package com.jhanantezana.jugueria.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.ReportingTables;

// The whole path: HTTP command, event, after-commit listener, read-model row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportingEndToEndIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID cashier = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		ReportingTables.clean(jdbc);
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aShiftWithASaleAndItsVoidLeavesSalesLinesExceptionsAndASummary() throws Exception {
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		var juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.pinnedForQuickSale(jdbc, juice.productId());
		var shift = id(post("/api/v1/shifts", "{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}"));
		var sale = id(post("/api/v1/quick-sales", """
				{"lines":[{"productId":"%s","modifierOptionIds":["%s"],"quantity":2,"note":null,"allergy":false}],
				 "payment":{"method":"CARD"}}""".formatted(juice.productId(), juice.large())));
		post("/api/v1/tickets/" + sale + "/void", "{\"reason\":\"Changed mind\",\"note\":\"Rang the wrong order\"}");
		post("/api/v1/shifts/" + shift + "/close", "{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(jdbc.sql("select quantity from reporting.sales_line where ticket_id = ? order by quantity")
				.param(UUID.fromString(sale))
				.query(Integer.class)
				.list()).containsExactly(-2, 2);
			assertThat(jdbc.sql("select type from reporting.exception_event where ticket_id = ?")
				.param(UUID.fromString(sale))
				.query(String.class)
				.list()).containsExactly("SALE_VOID");
			assertThat(jdbc.sql("select count(*) from reporting.shift_summary where shift_id = ? and closed_at is not null "
					+ "and opened_at is not null").param(UUID.fromString(shift)).query(Long.class).single()).isEqualTo(1);
		});
	}

	@Test
	void aCompedLineLeavesAZeroValueSalesLineAndAnExceptionAndTheRestIsSold() throws Exception {
		InstoreTables.aCompReason(jdbc, "Staff meal");
		var juice = InstoreTables.aSizedProduct(jdbc);
		post("/api/v1/shifts", "{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}");
		var ticket = id(post("/api/v1/tickets", "{\"counterName\":\"Ana\"}"));
		var line = "{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
			.formatted(juice.productId(), juice.small());
		post("/api/v1/tickets/" + ticket + "/lines", line);
		post("/api/v1/tickets/" + ticket + "/lines", line);
		post("/api/v1/tickets/" + ticket + "/send", "");
		var comped = jdbc.sql("select id from instore.ticket_line where ticket_id = ? order by position limit 1")
			.param(UUID.fromString(ticket))
			.query(UUID.class)
			.single();
		post("/api/v1/tickets/" + ticket + "/lines/" + comped + "/comp", "{\"reason\":\"Staff meal\"}");
		post("/api/v1/tickets/" + ticket + "/payments",
				"{\"method\":\"CARD\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"}}");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(jdbc.sql("select line_total_amount from reporting.sales_line where ticket_id = ? order by line_total_amount")
				.param(UUID.fromString(ticket))
				.query(java.math.BigDecimal.class)
				.list()).usingElementComparator(java.math.BigDecimal::compareTo)
				.containsExactly(new java.math.BigDecimal("0.00"), new java.math.BigDecimal("5.00"));
			assertThat(jdbc.sql("select comped from reporting.sales_line where ticket_id = ? and line_id = ?")
				.param(UUID.fromString(ticket))
				.param(comped)
				.query(Boolean.class)
				.single()).isTrue();
			assertThat(jdbc.sql("select type from reporting.exception_event where ticket_id = ?")
				.param(UUID.fromString(ticket))
				.query(String.class)
				.list()).containsExactly("COMP");
		});
	}

	private String post(String uri, String body) throws Exception {
		var result = mvc.post()
			.uri(uri)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
		assertThat(result.getResponse().getStatus()).isLessThan(HttpStatus.MULTIPLE_CHOICES.value());
		return result.getResponse().getContentAsString();
	}

	private static String id(String json) {
		var start = json.indexOf("\"id\":\"") + 6;
		return json.substring(start, json.indexOf('"', start));
	}

}
