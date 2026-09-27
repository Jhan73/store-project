package com.jhanantezana.jugueria.notifications.internal;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;

/**
 * Authenticates the STOMP {@code CONNECT} frame and authorizes {@code SUBSCRIBE}/client {@code SEND}
 * frames. The HTTP filter chain already permits the {@code /ws} handshake itself (tech-spec §7.1); this
 * is where the actual identity check happens.
 */
// Absent in a headless run such as the first-admin bootstrap, which opens no channel to intercept.
@Component
@ConditionalOnWebApplication(type = Type.SERVLET)
class StompAuthChannelInterceptor implements ChannelInterceptor {

	// Only the two topics this work package wires are allowed; anything else (board, tables, display,
	// user queues) is denied until its own work package defines who may subscribe to it.
	private static final Set<String> PUBLIC_DESTINATIONS = Arrays.stream(RealtimeTopic.values())
		.map(RealtimeTopic::destination)
		.collect(Collectors.toUnmodifiableSet());

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtDecoder jwtDecoder;

	private final JwtAuthenticationConverter jwtAuthenticationConverter;

	StompAuthChannelInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
		this.jwtDecoder = jwtDecoder;
		this.jwtAuthenticationConverter = jwtAuthenticationConverter;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		var accessor = StompHeaderAccessor.wrap(message);
		var command = accessor.getCommand();
		if (command == null) {
			return message;
		}
		switch (command) {
			case CONNECT -> authenticate(accessor);
			case SUBSCRIBE -> authorizeSubscribe(accessor);
			case SEND -> rejectClientSend(accessor);
			default -> {
				// UNSUBSCRIBE/DISCONNECT/ACK/etc. need no extra check here.
			}
		}
		// wrap() copies headers into a detached accessor; the mutated user/headers only take effect
		// once rebuilt into the message returned here, which is what the channel actually forwards.
		return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
	}

	// Anonymous CONNECT is allowed: /topic/catalog and /topic/store-status permit anonymous subscribers.
	private void authenticate(StompHeaderAccessor accessor) {
		var header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
		if (header == null) {
			return;
		}
		var token = header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
		try {
			var jwt = jwtDecoder.decode(token);
			accessor.setUser(jwtAuthenticationConverter.convert(jwt));
		}
		catch (JwtException e) {
			throw new MessagingException("Invalid or expired token");
		}
	}

	private void authorizeSubscribe(StompHeaderAccessor accessor) {
		var destination = accessor.getDestination();
		if (destination == null || !PUBLIC_DESTINATIONS.contains(destination)) {
			throw new MessagingException("Subscription not allowed for this destination");
		}
	}

	// The simple broker also relays client SEND frames addressed to a broker destination; only server
	// code (SimpMessagingTemplate) may publish to /topic/**.
	private void rejectClientSend(StompHeaderAccessor accessor) {
		var destination = accessor.getDestination();
		if (destination != null && destination.startsWith("/topic/")) {
			throw new MessagingException("Clients may not publish to broker destinations");
		}
	}

}
