package com.jhanantezana.testsupport;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import com.jhanantezana.jugueria.shared.Role;

// Skips real token signing; SecurityIT covers that path.
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
