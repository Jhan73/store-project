package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

	private final StompSessionRegistry sessions = mock(StompSessionRegistry.class);

	private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtDecoder,
			jwtAuthenticationConverter, sessions);

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
	void tellsTheRegistryWhichUserASessionBelongsToSoItCanBeClosedLater() {
		var jwt = mock(Jwt.class);
		var authentication = new TestingAuthenticationToken("user-1", null, List.of());
		when(jwtDecoder.decode("good-token")).thenReturn(jwt);
		when(jwtAuthenticationConverter.convert(jwt)).thenReturn(authentication);
		var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setSessionId("session-1");
		accessor.setNativeHeader("Authorization", "Bearer good-token");

		interceptor.preSend(message(accessor), null);

		verify(sessions).authenticated("session-1", "user-1");
	}

	@Test
	void doesNotRegisterAnAnonymousSession() {
		var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setSessionId("session-1");

		interceptor.preSend(message(accessor), null);

		verify(sessions, never()).authenticated(any(), any());
	}

	// StompSubProtocolHandler reads the user back from the accessor it built the message with, to authenticate the
	// session's later frames, so the interceptor must set it on that accessor and not on a copy.
	@Test
	void setsTheUserOnTheOriginalAccessorSoLaterFramesOfTheSessionAreAuthenticated() {
		var jwt = mock(Jwt.class);
		var authentication = new TestingAuthenticationToken("user", null, List.of());
		when(jwtDecoder.decode("good-token")).thenReturn(jwt);
		when(jwtAuthenticationConverter.convert(jwt)).thenReturn(authentication);
		var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setNativeHeader("Authorization", "Bearer good-token");
		accessor.setLeaveMutable(true);

		interceptor.preSend(message(accessor), null);

		assertThat(accessor.getUser()).isEqualTo(authentication);
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
		accessor.setDestination("/topic/tables");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsSubscribingToTheSessionsControlTopicEvenAsAdmin() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/sessions");
		accessor.setUser(new TestingAuthenticationToken("user", null, "ROLE_ADMIN"));

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ROLE_SERVER", "ROLE_CASHIER", "ROLE_ADMIN" })
	void allowsStaffToSubscribeToTheBoard(String role) {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/board");
		accessor.setUser(new TestingAuthenticationToken("user", null, role));

		assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
	}

	@Test
	void rejectsACustomerSubscribingToTheBoard() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/board");
		accessor.setUser(new TestingAuthenticationToken("user", null, "ROLE_CUSTOMER"));

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsAnAnonymousSubscriberToTheBoard() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/board");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsAnUnauthenticatedTokenSubscribingToTheBoard() {
		var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/board");
		var notAuthenticated = new TestingAuthenticationToken("user", null, "ROLE_SERVER");
		notAuthenticated.setAuthenticated(false);
		accessor.setUser(notAuthenticated);

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsClientSendToABrokerDestination() {
		var accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/topic/catalog");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsClientSendToAnApplicationDestination() {
		var accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/whatever");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsClientSendToAnyOtherDestination() {
		var accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/something/else");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
	}

	@Test
	void rejectsClientSendWithNoDestinationAtAll() {
		var accessor = StompHeaderAccessor.create(StompCommand.SEND);

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
