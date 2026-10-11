package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class StompSessionRegistryTest {

	private final StompSessionRegistry registry = new StompSessionRegistry();

	@Test
	void closesEverySessionOfTheUser() throws IOException {
		var first = socket("s1");
		var second = socket("s2");
		registry.opened(first);
		registry.opened(second);
		registry.authenticated("s1", "user-1");
		registry.authenticated("s2", "user-1");

		registry.closeSessionsOf("user-1");

		verify(first).close(any(CloseStatus.class));
		verify(second).close(any(CloseStatus.class));
	}

	@Test
	void leavesOtherUsersAndAnonymousSessionsAlone() throws IOException {
		var other = socket("s1");
		var anonymous = socket("s2");
		registry.opened(other);
		registry.opened(anonymous);
		registry.authenticated("s1", "user-2");

		registry.closeSessionsOf("user-1");

		verify(other, never()).close(any(CloseStatus.class));
		verify(anonymous, never()).close(any(CloseStatus.class));
	}

	@Test
	void closesASessionOnlyOnce() throws IOException {
		var session = socket("s1");
		registry.opened(session);
		registry.authenticated("s1", "user-1");

		registry.closeSessionsOf("user-1");
		registry.closeSessionsOf("user-1");

		verify(session).close(any(CloseStatus.class));
	}

	@Test
	void forgetsASessionThatClosedByItself() throws IOException {
		var session = socket("s1");
		registry.opened(session);
		registry.authenticated("s1", "user-1");

		registry.closed("s1");
		registry.closeSessionsOf("user-1");

		verify(session, never()).close(any(CloseStatus.class));
	}

	@Test
	void keepsClosingTheRestWhenOneSessionFailsToClose() throws IOException {
		var broken = socket("s1");
		doThrow(new IOException("already gone")).when(broken).close(any(CloseStatus.class));
		var healthy = socket("s2");
		registry.opened(broken);
		registry.opened(healthy);
		registry.authenticated("s1", "user-1");
		registry.authenticated("s2", "user-1");

		assertThatCode(() -> registry.closeSessionsOf("user-1")).doesNotThrowAnyException();

		verify(healthy).close(any(CloseStatus.class));
	}

	@Test
	void ignoresAnAuthenticationForASessionThatIsNotOpen() {
		registry.authenticated("unknown", "user-1");

		assertThatCode(() -> registry.closeSessionsOf("user-1")).doesNotThrowAnyException();
	}

	private static WebSocketSession socket(String id) {
		var session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(id);
		return session;
	}

}
