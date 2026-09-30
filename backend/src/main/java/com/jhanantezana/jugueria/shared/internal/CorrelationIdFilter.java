package com.jhanantezana.jugueria.shared.internal;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jhanantezana.jugueria.shared.CorrelationId;
import com.jhanantezana.jugueria.shared.Ids;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class CorrelationIdFilter extends OncePerRequestFilter {

	// The value is client-controlled and ends up in logs and audit rows.
	private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var id = request.getHeader(CorrelationId.HEADER);
		if (id == null || !WELL_FORMED.matcher(id).matches()) {
			id = Ids.newId().toString();
		}
		response.setHeader(CorrelationId.HEADER, id);
		MDC.put(CorrelationId.MDC_KEY, id);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(CorrelationId.MDC_KEY);
		}
	}

}
