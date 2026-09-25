package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jhanantezana.jugueria.shared.CorrelationId;

class CorrelationIdFilterTest {

	final CorrelationIdFilter filter = new CorrelationIdFilter();

	@Test
	void reusesAWellFormedIncomingId() throws Exception {
		var response = run(request("abc-123_DEF"), new AtomicReference<>());

		assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123_DEF");
	}

	@Test
	void generatesAnIdWhenNoneArrives() throws Exception {
		var response = run(new MockHttpServletRequest(), new AtomicReference<>());

		assertThat(response.getHeader("X-Request-Id")).hasSize(36);
	}

	@Test
	void replacesAnIdThatCouldForgeLogLines() throws Exception {
		var response = run(request("x\n{\"level\":\"ERROR\"}"), new AtomicReference<>());

		assertThat(response.getHeader("X-Request-Id")).hasSize(36).doesNotContain("\n");
	}

	@Test
	void replacesAnOversizedId() throws Exception {
		var response = run(request("a".repeat(65)), new AtomicReference<>());

		assertThat(response.getHeader("X-Request-Id")).hasSize(36);
	}

	@Test
	void exposesTheIdDuringTheRequestAndClearsItAfterwards() throws Exception {
		var seen = new AtomicReference<String>();

		var response = run(request("req-1"), seen);

		assertThat(seen.get()).isEqualTo("req-1");
		assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-1");
		assertThat(CorrelationId.current()).isEmpty();
		assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
	}

	private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<String> seen)
			throws Exception {
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> seen.set(CorrelationId.current().orElse(null)));
		return response;
	}

	private static MockHttpServletRequest request(String id) {
		var request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", id);
		return request;
	}

}
