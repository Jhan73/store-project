package com.jhanantezana.jugueria.identity.internal.security;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.jhanantezana.jugueria.shared.CorrelationId;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemSecurityHandlers problems,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
		return http
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
						"/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/verify")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/catalog/menu", "/api/v1/store/status")
				.permitAll()
				// Authenticated by the provider's signature inside the payments adapter.
				.requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health/**")
				.permitAll()
				// Authenticated on the STOMP CONNECT frame instead.
				.requestMatchers(HttpMethod.GET, "/ws")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(problems)
				.accessDeniedHandler(problems))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
			.cors(Customizer.withDefaults())
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
		var cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.allowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.IF_MATCH,
				"Idempotency-Key", "X-Requested-With", CorrelationId.HEADER));
		cors.setExposedHeaders(List.of(HttpHeaders.ETAG, HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER,
				"Idempotent-Replayed", CorrelationId.HEADER));
		cors.setAllowCredentials(true);
		cors.setMaxAge(Duration.ofHours(1));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cors);
		return source;
	}

}
