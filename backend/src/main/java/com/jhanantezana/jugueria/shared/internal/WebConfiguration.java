package com.jhanantezana.jugueria.shared.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.jhanantezana.jugueria.shared.WebOriginsProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebOriginsProperties.class)
class WebConfiguration {

	// Ahead of Spring Security, so 401 and 403 responses carry the correlation ID too.
	@Bean
	FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
		var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
