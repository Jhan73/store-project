package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jhanantezana.probe.ProbeController;

@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsAdviceTest {

	final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ProbeController())
		.setControllerAdvice(new ProblemDetailsAdvice())
		.addFilters(new CorrelationIdFilter())
		.build());

	@Test
	void rendersABusinessExceptionWithItsCodeAndProperties() {
		var result = mvc.get().uri("/probe/business").exchange();

		assertProblem(result, HttpStatus.CONFLICT, "probe.out-of-stock");
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Product has 2 units left");
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Conflict");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/probe/business");
	}

	@Test
	void listsEveryBeanValidationFailure() {
		var result = mvc.post()
			.uri("/probe/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"note\":null,\"lines\":[{\"quantity\":0}]}")
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.validation-failed");
		assertThat(result).bodyJson().extractingPath("$.errors").asArray().hasSize(2);
		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'lines[0].quantity')].constraint")
			.asArray()
			.containsExactly("Positive");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'note')].constraint")
			.asArray()
			.containsExactly("NotNull");
	}

	@Test
	void neverLogsTheRejectedValues(CapturedOutput output) {
		mvc.post()
			.uri("/probe/orders")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"note\":\"ana@example.com\",\"lines\":[{\"quantity\":-987654}]}")
			.exchange();

		assertThat(output).contains("common.validation-failed").doesNotContain("-987654");
	}

	@Test
	void listsParameterValidationFailures() {
		var result = mvc.get().uri("/probe/quantity?value=0").exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.validation-failed");
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("value");
		assertThat(result).bodyJson().extractingPath("$.errors[0].constraint").isEqualTo("Positive");
	}

	@Test
	void leavesAccessDecisionsToSpringSecurity() {
		assertThat(mvc.get().uri("/probe/denied").exchange()).hasFailed()
			.failure()
			.hasRootCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void rejectsMalformedJson() {
		var result = mvc.post().uri("/probe/orders").contentType(MediaType.APPLICATION_JSON).content("{").exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.malformed-request");
	}

	@Test
	void rejectsAMissingRequiredHeader() {
		assertProblem(mvc.get().uri("/probe/header").exchange(), HttpStatus.BAD_REQUEST, "common.malformed-request");
	}

	@Test
	void reportsAnOptimisticLockFailureAsAConcurrentModification() {
		assertProblem(mvc.get().uri("/probe/locked").exchange(), HttpStatus.CONFLICT, "common.concurrent-modification");
	}

	@Test
	void hidesTheCauseOfAnUnexpectedError() {
		var result = mvc.get().uri("/probe/boom").exchange();

		assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "common.internal-error");
		assertThat(result).bodyText().doesNotContain("secret");
	}

	@Test
	void namesOtherFrameworkErrorsAfterTheirStatus() {
		assertProblem(mvc.get().uri("/probe/missing").exchange(), HttpStatus.NOT_FOUND, "common.not-found");
		assertProblem(mvc.delete().uri("/probe/business").exchange(), HttpStatus.METHOD_NOT_ALLOWED,
				"common.method-not-allowed");
		assertProblem(mvc.post().uri("/probe/orders").contentType(MediaType.TEXT_PLAIN).content("x").exchange(),
				HttpStatus.UNSUPPORTED_MEDIA_TYPE, "common.unsupported-media-type");
	}

	@Test
	void carriesTheCorrelationIdOfTheResponseHeader() {
		var result = mvc.get().uri("/probe/business").header("X-Request-Id", "req-123").exchange();

		assertThat(result).hasHeader("X-Request-Id", "req-123");
		assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("req-123");
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
		assertThat(result).bodyJson().extractingPath("$.correlationId").isNotNull();
	}

}
