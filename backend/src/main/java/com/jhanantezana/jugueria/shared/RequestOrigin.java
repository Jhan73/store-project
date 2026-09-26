package com.jhanantezana.jugueria.shared;

import org.jspecify.annotations.Nullable;

public interface RequestOrigin {

	/** The resolved client address (behind trusted proxies), or {@code null} outside a request. */
	@Nullable String clientIp();

	/** The request's User-Agent header, or {@code null} outside a request. */
	@Nullable String userAgent();

}
