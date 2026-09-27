package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Absent in a headless run such as the first-admin bootstrap, which serves no requests (mirrors SecurityConfiguration).
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableWebSocketMessageBroker
class StompConfiguration implements WebSocketMessageBrokerConfigurer {

	// Below the ALB's 60s idle timeout (tech-spec §8.1), so a connection is never dropped for being idle.
	private static final long HEARTBEAT_MILLIS = 20_000;

	private final StompAuthChannelInterceptor authInterceptor;

	private final Environment environment;

	StompConfiguration(StompAuthChannelInterceptor authInterceptor, Environment environment) {
		this.authInterceptor = authInterceptor;
		this.environment = environment;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins());
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		var heartbeatScheduler = new ThreadPoolTaskScheduler();
		heartbeatScheduler.setPoolSize(1);
		heartbeatScheduler.setThreadNamePrefix("stomp-heartbeat-");
		heartbeatScheduler.initialize();
		registry.enableSimpleBroker("/topic")
			.setHeartbeatValue(new long[] { HEARTBEAT_MILLIS, HEARTBEAT_MILLIS })
			.setTaskScheduler(heartbeatScheduler);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}

	// Same allowlist CORS uses (jugueria.identity.allowed-origins); never "*" (tech-spec §4.4/§7.1).
	private String[] allowedOrigins() {
		var origins = environment.getProperty("jugueria.identity.allowed-origins", String[].class);
		return origins != null ? origins : new String[0];
	}

}
