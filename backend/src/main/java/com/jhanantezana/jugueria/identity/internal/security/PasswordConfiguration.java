package com.jhanantezana.jugueria.identity.internal.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

// Separate from the web security chain: the headless first-admin bootstrap hashes passwords too.
@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
