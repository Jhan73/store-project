package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ServletRequestOriginTest {

	final ServletRequestOrigin origin = new ServletRequestOrigin();

	@AfterEach
	void clearContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void isEmptyOutsideAnyRequest() {
		assertThat(origin.clientIp()).isNull();
		assertThat(origin.userAgent()).isNull();
	}

	@Test
	void readsTheResolvedRemoteAddressAndUserAgent() {
		var request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");
		request.addHeader("User-Agent", "junit");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		assertThat(origin.clientIp()).isEqualTo("203.0.113.7");
		assertThat(origin.userAgent()).isEqualTo("junit");
	}

}
