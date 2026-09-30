package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackendApplicationTest {

	@Test
	void isBootstrapOnlyWhenTheFlagIsPresent() {
		assertThat(BackendApplication.isBootstrapOnly(new String[] { "--bootstrap-first-admin", "--admin-email=x@y.com" }))
			.isTrue();
	}

	@Test
	void isNotBootstrapOnlyDuringAnOrdinaryStart() {
		assertThat(BackendApplication.isBootstrapOnly(new String[] {})).isFalse();
		assertThat(BackendApplication.isBootstrapOnly(new String[] { "--server.port=8080" })).isFalse();
	}

}
