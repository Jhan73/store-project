package com.jhanantezana.jugueria.shared.internal;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jhanantezana.jugueria.shared.RequestOrigin;

import jakarta.servlet.http.HttpServletRequest;

@Component
class ServletRequestOrigin implements RequestOrigin {

	@Override
	public @Nullable String clientIp() {
		return request().map(HttpServletRequest::getRemoteAddr).orElse(null);
	}

	@Override
	public @Nullable String userAgent() {
		return request().map(r -> r.getHeader("User-Agent")).orElse(null);
	}

	private static Optional<HttpServletRequest> request() {
		var attributes = RequestContextHolder.getRequestAttributes();
		return attributes instanceof ServletRequestAttributes servletAttributes
				? Optional.of(servletAttributes.getRequest()) : Optional.empty();
	}

}
