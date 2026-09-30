package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jhanantezana.jugueria.shared.Role;

class SecurityCurrentActorTest {

	final SecurityCurrentActor actor = new SecurityCurrentActor();

	@BeforeEach
	@AfterEach
	void clearContexts() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void isTheAuthenticatedUser() {
		var id = UUID.randomUUID();
		authenticate(new TestingAuthenticationToken(id.toString(), null, "ROLE_CASHIER"));

		assertThat(actor.id()).isEqualTo(id);
		assertThat(actor.role()).isEqualTo(Role.CASHIER);
		assertThat(actor.isSystem()).isFalse();
	}

	@Test
	void isSystemOutsideAnyRequest() {
		assertThat(actor.isSystem()).isTrue();
		assertThat(actor.id()).isNull();
		assertThat(actor.role()).isNull();
	}

	@Test
	void isAnonymousInARequestWithoutAUser() {
		inRequest();

		assertThat(actor.isSystem()).isFalse();
		assertThat(actor.id()).isNull();
		assertThat(actor.role()).isNull();
	}

	@Test
	void treatsSpringSecuritysAnonymousTokenAsAnonymous() {
		inRequest();
		authenticate(new AnonymousAuthenticationToken("key", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThat(actor.isSystem()).isFalse();
		assertThat(actor.id()).isNull();
	}

	@Test
	void rejectsAUserWithoutExactlyOneRole() {
		authenticate(new TestingAuthenticationToken(UUID.randomUUID().toString(), null,
				List.of()));

		assertThatIllegalStateException().isThrownBy(actor::role);
	}

	private static void authenticate(Authentication authentication) {
		authentication.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private static void inRequest() {
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
	}

}
