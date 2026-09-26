package com.jhanantezana.testsupport;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import com.jhanantezana.jugueria.shared.Role;

// Reusable MockMvc auth for tests that only need "a CASHIER" without issuing and signing a real
// token; SecurityIT covers the real token path (signing, expiry, issuer/audience) end to end.
public final class AuthenticatedAs {

	private AuthenticatedAs() {
	}

	public static JwtRequestPostProcessor user(UUID id, Role role) {
		// Mirrors JwtConfiguration.jwtAuthenticationConverter(): claim "roles", prefix "ROLE_".
		var authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		return SecurityMockMvcRequestPostProcessors.jwt()
			.jwt(jwt -> jwt.subject(id.toString()).claim("roles", List.of(role.name())))
			.authorities(authorities);
	}

}
