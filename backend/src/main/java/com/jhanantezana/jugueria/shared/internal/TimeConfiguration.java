package com.jhanantezana.jugueria.shared.internal;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
