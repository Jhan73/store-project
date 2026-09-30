package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.jhanantezana.jugueria.shared.WebOriginsProperties;

// Absent in a headless run such as the first-admin bootstrap, which serves no requests (mirrors SecurityConfiguration).
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableWebSocketMessageBroker
class StompConfiguration implements WebSocketMessageBrokerConfigurer {

	// Below the ALB's 60s idle timeout, so a connection is never dropped for being idle.
	private static final long HEARTBEAT_MILLIS = 20_000;

	private final StompAuthChannelInterceptor authInterceptor;

	private final WebOriginsProperties webOrigins;

	private final ThreadPoolTaskScheduler heartbeatTaskScheduler;

	StompConfiguration(StompAuthChannelInterceptor authInterceptor, WebOriginsProperties webOrigins,
			ThreadPoolTaskScheduler heartbeatTaskScheduler) {
		this.authInterceptor = authInterceptor;
		this.webOrigins = webOrigins;
		this.heartbeatTaskScheduler = heartbeatTaskScheduler;
	}

	// A Spring-managed bean so the scheduler's thread pool is shut down with the context, not leaked.
	@Bean
	static ThreadPoolTaskScheduler heartbeatTaskScheduler() {
		var scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("stomp-heartbeat-");
		return scheduler;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(webOrigins.allowedOrigins().toArray(new String[0]));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic")
			.setHeartbeatValue(new long[] { HEARTBEAT_MILLIS, HEARTBEAT_MILLIS })
			.setTaskScheduler(heartbeatTaskScheduler);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}

}
