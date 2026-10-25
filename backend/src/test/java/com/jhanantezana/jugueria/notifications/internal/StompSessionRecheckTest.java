package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.jhanantezana.jugueria.shared.AccountActivity;

class StompSessionRecheckTest {

	private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

	private final StompSessionRegistry registry = new StompSessionRegistry();

	private final AccountActivity accounts = mock(AccountActivity.class);

	private final StompSessionRecheckProperties properties = new StompSessionRecheckProperties(Duration.ofSeconds(30),
			Duration.ofMinutes(5));

	private final StompSessionRecheck recheck = new StompSessionRecheck(registry, accounts, properties,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final UUID user = UUID.randomUUID();

	private WebSocketSession open(String id, Instant expires) {
		var socket = mock(WebSocketSession.class);
		when(socket.getId()).thenReturn(id);
		registry.opened(socket);
		registry.authenticated(id, user.toString(), expires);
		return socket;
	}

	@Test
	void closesTheSessionsOfAnAccountThatIsNoLongerActiveOnceTheirTokenExpired() throws IOException {
		var socket = open("s1", NOW.minusSeconds(1));
		when(accounts.isActive(user)).thenReturn(false);

		recheck.run();

		verify(socket).close(any(CloseStatus.class));
	}

	@Test
	void keepsTheSessionsOfAnActiveAccountAndAsksAgainOnlyAfterTheRecheckInterval() throws IOException {
		var socket = open("s1", NOW.minusSeconds(1));
		when(accounts.isActive(user)).thenReturn(true);

		recheck.run();

		verify(socket, never()).close(any(CloseStatus.class));
		assertThat(registry.usersDue(NOW.plus(Duration.ofMinutes(4)))).isEmpty();
		assertThat(registry.usersDue(NOW.plus(Duration.ofMinutes(5)))).containsExactly(user.toString());
	}

	@Test
	void doesNotLookAnAccountUpBeforeItsTokenExpired() {
		open("s1", NOW.plusSeconds(60));

		recheck.run();

		org.mockito.Mockito.verifyNoInteractions(accounts);
	}

	@Test
	void keepsTheSessionAndTriesAgainOnTheNextRunWhenTheAccountLookupFails() throws IOException {
		var socket = open("s1", NOW.minusSeconds(1));
		when(accounts.isActive(user)).thenThrow(new IllegalStateException("database down"));

		recheck.run();

		verify(socket, never()).close(any(CloseStatus.class));
		assertThat(registry.usersDue(NOW)).containsExactly(user.toString());
	}

	@Test
	void ignoresASessionWhoseSubjectIsNotAnAccountId() throws IOException {
		var socket = mock(WebSocketSession.class);
		when(socket.getId()).thenReturn("s1");
		registry.opened(socket);
		registry.authenticated("s1", "not-a-uuid", NOW.minusSeconds(1));

		recheck.run();

		verify(socket, never()).close(any(CloseStatus.class));
	}

	@Test
	void registersAFixedDelayTaskWithTheSweepInterval() {
		var registrar = new ScheduledTaskRegistrar();

		recheck.configureTasks(registrar);

		assertThat(registrar.getFixedDelayTaskList()).singleElement()
			.satisfies(task -> assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(30)));
	}

}
