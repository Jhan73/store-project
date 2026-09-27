package com.jhanantezana.archfixtures;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

public class RequestContextBypass {

	public Object[] readContexts() {
		return new Object[] { RequestContextHolder.getRequestAttributes(), SecurityContextHolder.getContext() };
	}

}
