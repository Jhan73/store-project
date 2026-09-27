package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class StompAuthChannelInterceptorTest {

	private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);

	private final JwtAuthenticationConverter jwtAuthenticationConverter = mock(JwtAuthenticationConverter.class);

	private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtDecoder,
			jwtAuthenticationConverter);

	@Test
	void allowsAnonymousConnectWithoutAToken() {
		var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

		var result = interceptor.preSend(message(accessor), null);

		assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
	}

	@Test
	void setsTheUserOnConnectWithAValidToken() {
		var jwt = mock(Jwt.class);
		var authentication = new TestingAuthenticationToken("user", null, List.of());
		when(jwtDecoder.decode("good-token")).thenReturn(jwt);
		when(jwtAuthenticationConverter.convert(jwt)).thenReturn(authentication);
		var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setNativeHeader("Authorization", "Bearer good-token");

		var result = interceptor.preSend(message(accessor), null);

		assertThat(StompHeaderAccessor.wrap(result).getUser()).isEqualTo(authentication);
	}

	@Test
	void rejectsConnectWithAnInvalidToken() {
		when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid"));
		var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setNativeHeader("Authorization", "Bearer bad-token");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void allowsSubscribingToCatalog() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/catalog");

		assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
	}

	@Test
	void allowsSubscribingToStoreStatus() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/store-status");

		assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
	}

	@Test
	void rejectsSubscribingToAnUnlistedDestination() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/board");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsClientSendToABrokerDestination() {
		var accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/topic/catalog");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void leavesOtherFrameTypesAlone() {
		var accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);

		assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
	}

	private static Message<byte[]> message(StompHeaderAccessor accessor) {
		return org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],
				accessor.getMessageHeaders());
	}

}
