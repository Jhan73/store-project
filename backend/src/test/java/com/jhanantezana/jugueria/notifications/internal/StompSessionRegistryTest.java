package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class StompSessionRegistryTest {

	private static final Instant SOON = Instant.parse("2026-10-25T10:00:00Z");

	private static final Instant LATER = Instant.parse("2026-10-25T10:30:00Z");

	private final StompSessionRegistry registry = new StompSessionRegistry();

	@Test
	void closesEverySessionOfTheUser() throws IOException {
		var first = socket("s1");
		var second = socket("s2");
		registry.opened(first);
		registry.opened(second);
		registry.authenticated("s1", "user-1", LATER);
		registry.authenticated("s2", "user-1", LATER);

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
		registry.authenticated("s1", "user-2", LATER);

		registry.closeSessionsOf("user-1");

		verify(other, never()).close(any(CloseStatus.class));
		verify(anonymous, never()).close(any(CloseStatus.class));
	}

	@Test
	void closesASessionOnlyOnce() throws IOException {
		var session = socket("s1");
		registry.opened(session);
		registry.authenticated("s1", "user-1", LATER);

		registry.closeSessionsOf("user-1");
		registry.closeSessionsOf("user-1");

		verify(session).close(any(CloseStatus.class));
	}

	@Test
	void forgetsASessionThatClosedByItself() throws IOException {
		var session = socket("s1");
		registry.opened(session);
		registry.authenticated("s1", "user-1", LATER);

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
		registry.authenticated("s1", "user-1", LATER);
		registry.authenticated("s2", "user-1", LATER);

		assertThatCode(() -> registry.closeSessionsOf("user-1")).doesNotThrowAnyException();

		verify(healthy).close(any(CloseStatus.class));
	}

	@Test
	void ignoresAnAuthenticationForASessionThatIsNotOpen() {
		registry.authenticated("unknown", "user-1", LATER);

		assertThatCode(() -> registry.closeSessionsOf("user-1")).doesNotThrowAnyException();
	}

	@Test
	void reportsAUserOnlyOnceTheTokenItsSessionConnectedWithHasExpired() {
		var session = socket("s1");
		registry.opened(session);
		registry.authenticated("s1", "user-1", SOON);

		assertThat(registry.usersDue(SOON.minusSeconds(1))).isEmpty();
		assertThat(registry.usersDue(SOON)).containsExactly("user-1");
	}

	@Test
	void reportsAUserWithSeveralDueSessionsOnce() {
		registry.opened(socket("s1"));
		registry.opened(socket("s2"));
		registry.authenticated("s1", "user-1", SOON);
		registry.authenticated("s2", "user-1", SOON);

		assertThat(registry.usersDue(SOON)).containsExactly("user-1");
	}

	@Test
	void aRecheckedUserIsNotDueAgainBeforeTheNextCheck() {
		registry.opened(socket("s1"));
		registry.authenticated("s1", "user-1", SOON);

		registry.rechecked("user-1", LATER);

		assertThat(registry.usersDue(SOON.plusSeconds(1))).isEmpty();
		assertThat(registry.usersDue(LATER)).containsExactly("user-1");
	}

	@Test
	void neverReportsAnAnonymousSession() {
		registry.opened(socket("s1"));

		assertThat(registry.usersDue(LATER.plusSeconds(3600))).isEmpty();
	}

	private static WebSocketSession socket(String id) {
		var session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(id);
		return session;
	}

}
