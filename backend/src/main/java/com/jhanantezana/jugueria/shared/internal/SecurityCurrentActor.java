package com.jhanantezana.jugueria.shared.internal;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Role;

@Component
class SecurityCurrentActor implements CurrentActor {

	private static final String ROLE_PREFIX = "ROLE_";

	@Override
	public @Nullable UUID id() {
		return user().map(authentication -> UUID.fromString(authentication.getName())).orElse(null);
	}

	@Override
	public @Nullable Role role() {
		return user().map(SecurityCurrentActor::roleOf).orElse(null);
	}

	@Override
	public boolean isSystem() {
		return user().isEmpty() && RequestContextHolder.getRequestAttributes() == null;
	}

	private static Optional<Authentication> user() {
		return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
			.filter(Authentication::isAuthenticated)
			.filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken));
	}

	private static Role roleOf(Authentication authentication) {
		var roles = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
			.map(authority -> Role.valueOf(authority.substring(ROLE_PREFIX.length())))
			.toList();
		if (roles.size() != 1) {
			throw new IllegalStateException("A user has exactly one role, found " + roles);
		}
		return roles.getFirst();
	}

}
