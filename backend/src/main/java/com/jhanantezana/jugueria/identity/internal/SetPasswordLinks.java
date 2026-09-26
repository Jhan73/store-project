package com.jhanantezana.jugueria.identity.internal;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;

final class SetPasswordLinks {

	private SetPasswordLinks() {
	}

	static URI build(IdentityProperties.SetPassword properties, String rawToken) {
		var encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
		return URI.create(properties.frontendBaseUrl() + properties.frontendPath() + "?token=" + encodedToken);
	}

}
