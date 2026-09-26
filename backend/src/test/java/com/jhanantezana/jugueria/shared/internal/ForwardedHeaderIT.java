package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.probe.RequestOriginProbeController;

// A real embedded server, not MockMvc: server.forward-headers-strategy=native is a Tomcat valve,
// invisible to MockMvc's mock dispatch. The test client itself is the trusted (loopback) peer.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, RequestOriginProbeController.class })
class ForwardedHeaderIT {

	@Value("${local.server.port}")
	int port;

	@Autowired
	AccessTokenIssuer issuer;

	@Test
	void recordsTheForwardedAddressFromATrustedProxyInsteadOfTheSocketPeer() {
		var token = issuer.issue(UUID.randomUUID(), Role.CASHIER);

		var body = RestClient.create()
			.get()
			.uri("http://localhost:" + port + "/api/v1/probe/request-origin")
			.header("Authorization", "Bearer " + token)
			.header("X-Forwarded-For", "203.0.113.7")
			.retrieve()
			.body(String.class);

		assertThat(body).contains("203.0.113.7");
	}

}
