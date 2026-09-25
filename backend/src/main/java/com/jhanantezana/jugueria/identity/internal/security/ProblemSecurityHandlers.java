package com.jhanantezana.jugueria.identity.internal.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.jhanantezana.jugueria.identity.AuthError;
import com.jhanantezana.jugueria.shared.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Filter-level failures never reach the MVC advice on their own, so they are handed to it.
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

	private final HandlerExceptionResolver resolver;

	ProblemSecurityHandlers(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		bearer.commence(request, response, authException);
		resolver.resolveException(request, response, null,
				new BusinessException(AuthError.UNAUTHENTICATED, "A valid access token is required"));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		resolver.resolveException(request, response, null,
				new BusinessException(AuthError.FORBIDDEN, "The role of the caller is not allowed here"));
	}

}
