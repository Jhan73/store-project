package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

// The first-admin bootstrap runs without a web server, so the context must start without one.
@SpringBootTest(webEnvironment = WebEnvironment.NONE,
		properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class HeadlessContextIT {

	@Autowired
	ApplicationContext context;

	@Test
	void startsWithoutAWebServerAndStillHashesPasswords() {
		assertThat(context.getBeanNamesForType(PasswordEncoder.class)).hasSize(1);
		assertThat(context.containsBean("firstAdminBootstrapRunner")).isTrue();
	}

}
