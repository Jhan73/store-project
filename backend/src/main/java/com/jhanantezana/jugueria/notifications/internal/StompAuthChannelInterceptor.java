package com.jhanantezana.jugueria.notifications.internal;

import java.security.Principal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
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
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.shared.Role;

// Absent in a headless run such as the first-admin bootstrap, which opens no channel to intercept.
@Component
@ConditionalOnWebApplication(type = Type.SERVLET)
class StompAuthChannelInterceptor implements ChannelInterceptor {

	// Only the wired topics can be subscribed to; every other destination is denied by default until it has its own rule.
	private static final Map<String, RealtimeTopic> TOPICS = Arrays.stream(RealtimeTopic.values())
		.filter(RealtimeTopic::subscribable)
		.collect(Collectors.toUnmodifiableMap(RealtimeTopic::destination, Function.identity()));

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtDecoder jwtDecoder;

	private final JwtAuthenticationConverter jwtAuthenticationConverter;

	private final StompSessionRegistry sessions;

	StompAuthChannelInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter,
			StompSessionRegistry sessions) {
		this.jwtDecoder = jwtDecoder;
		this.jwtAuthenticationConverter = jwtAuthenticationConverter;
		this.sessions = sessions;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		// Mutate the original accessor: the handler reads the user back from it for the session's later frames.
		var original = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		var accessor = original != null && original.isMutable() ? original : StompHeaderAccessor.wrap(message);
		var command = accessor.getCommand();
		if (command == null) {
			return message;
		}
		switch (command) {
			case CONNECT -> authenticate(accessor);
			case SUBSCRIBE -> authorizeSubscribe(accessor);
			case SEND -> rejectClientSend();
			default -> {
				// UNSUBSCRIBE/DISCONNECT/ACK/etc. need no extra check here.
			}
		}
		// A wrap() copy's changes only reach the channel once rebuilt into the message returned here.
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
			var authentication = jwtAuthenticationConverter.convert(jwt);
			accessor.setUser(authentication);
			if (authentication != null && accessor.getSessionId() != null) {
				sessions.authenticated(accessor.getSessionId(), authentication.getName());
			}
		}
		catch (JwtException e) {
			throw new MessagingException("Invalid or expired token");
		}
	}

	private void authorizeSubscribe(StompHeaderAccessor accessor) {
		var destination = accessor.getDestination();
		var topic = destination == null ? null : TOPICS.get(destination);
		if (topic == null) {
			throw new MessagingException("Subscription not allowed for this destination");
		}
		if (!topic.subscriberRoles().isEmpty() && !hasAnyRole(accessor.getUser(), topic.subscriberRoles())) {
			throw new MessagingException("Subscription not allowed for this user");
		}
	}

	private static boolean hasAnyRole(@Nullable Principal user, Set<Role> roles) {
		if (!(user instanceof Authentication authentication) || !authentication.isAuthenticated()) {
			return false;
		}
		var allowed = roles.stream().map(role -> "ROLE_" + role.name()).collect(Collectors.toSet());
		return authentication.getAuthorities().stream().anyMatch(authority -> allowed.contains(authority.getAuthority()));
	}

	// No @MessageMapping destinations exist, so no client SEND has anywhere legitimate to go; deny all of them.
	private void rejectClientSend() {
		throw new MessagingException("Clients may not send messages");
	}

}
