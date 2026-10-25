package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.jhanantezana.jugueria.shared.RealtimeSignalReceived;

import tools.jackson.databind.json.JsonMapper;

class AppEventsListenerTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

	private final JdbcConnectionDetails connectionDetails = mock(JdbcConnectionDetails.class);

	private final ApplicationEventPublisher localEvents = mock(ApplicationEventPublisher.class);

	private final NotificationsProperties.Ses ses = new NotificationsProperties.Ses("us-east-1", Duration.ofSeconds(10),
			Duration.ofSeconds(5));

	private final NotificationsProperties properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com",
			List.of(), ses, "jugueria-backend");

	private final AppEventsListener listener = new AppEventsListener(connectionDetails, messagingTemplate, mapper,
			properties, localEvents);

	@Test
	void forwardsANotificationToItsTopicDestinationWithoutTheTopicField() {
		listener.forward("{\"topic\":\"store-status\",\"type\":\"STORE_SETTINGS_CHANGED\",\"ids\":{\"settingsId\":\"abc\"}}");

		verify(messagingTemplate).convertAndSend(eq("/topic/store-status"),
				eq("{\"type\":\"STORE_SETTINGS_CHANGED\",\"ids\":{\"settingsId\":\"abc\"}}"), anyMap());
	}

	@Test
	void publishesTheSignalLocallyBeforeForwardingItSoAReactingModuleIsNeverStale() {
		listener.forward("{\"topic\":\"catalog\",\"type\":\"PRODUCT_CHANGED\",\"ids\":{\"productId\":\"abc\"}}");

		var order = inOrder(localEvents, messagingTemplate);
		order.verify(localEvents)
			.publishEvent(new RealtimeSignalReceived("catalog", "PRODUCT_CHANGED", Map.of("productId", "abc")));
		order.verify(messagingTemplate).convertAndSend(eq("/topic/catalog"), any(String.class), anyMap());
	}

	@Test
	void aFailingLocalReactionNeverStopsTheStompForward() {
		doThrow(new IllegalStateException("boom")).when(localEvents).publishEvent(any(Object.class));

		listener.forward("{\"topic\":\"catalog\",\"type\":\"PRODUCT_CHANGED\",\"ids\":{\"productId\":\"abc\"}}");

		verify(messagingTemplate).convertAndSend(eq("/topic/catalog"), any(String.class), anyMap());
	}

	@Test
	void ignoresAMalformedNotificationInsteadOfThrowing() {
		listener.forward("not json");

		verifyNoInteractions(messagingTemplate);
		verifyNoInteractions(localEvents);
	}

	@Test
	void anInterruptStopsTheLoopInsteadOfLettingItSpinWithoutBackoff() {
		var failing = new AppEventsListener(connectionDetails, messagingTemplate, mapper, properties, localEvents) {
			@Override
			void connect() throws SQLException {
				throw new SQLException("database not ready");
			}
		};
		var loop = new Thread(() -> {
			Thread.currentThread().interrupt();
			failing.runOnCallingThread();
		});
		loop.start();
		try {
			loop.join(Duration.ofSeconds(5));
			assertThat(loop.isAlive()).isFalse();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			failing.stop();
			loop.interrupt();
		}
	}

	@Test
	void isNotRunningUntilStarted() {
		assertThat(listener.isRunning()).isFalse();
	}

	// Drives the loop without a database: each poll() call does the next scripted action, connect() always succeeds.
	private class ScriptedListener extends AppEventsListener {

		final List<Duration> pauses = new ArrayList<>();

		final List<Integer> connectsBeforePoll = new ArrayList<>();

		private final Deque<PollAction> script;

		private int connects;

		private int failingConnects;

		ScriptedListener(PollAction... actions) {
			super(connectionDetails, messagingTemplate, mapper, properties, localEvents);
			this.script = new ArrayDeque<>(List.of(actions));
		}

		ScriptedListener failingFirstConnects(int count) {
			this.failingConnects = count;
			return this;
		}

		@Override
		void connect() throws SQLException {
			if (failingConnects > 0) {
				failingConnects--;
				throw new SQLException("database not ready");
			}
			connects++;
		}

		@Override
		void poll() throws SQLException {
			connectsBeforePoll.add(connects);
			script.removeFirst().run(this);
		}

		@Override
		void pause(Duration duration) {
			pauses.add(duration);
		}

	}

	@FunctionalInterface
	private interface PollAction {

		void run(AppEventsListener listener) throws SQLException;

	}

	private static final PollAction DROP = listener -> {
		throw new SQLException("connection lost");
	};

	private static final PollAction CRASH = listener -> {
		throw new IllegalStateException("unexpected");
	};

	private static final PollAction STOP = AppEventsListener::stop;

	private void run(ScriptedListener scripted) {
		// stop() is what ends the scripted run.
		scripted.runOnCallingThread();
	}

	private void verifyResyncSignalSentToEveryClientTopic(int times) {
		for (var topic : List.of("catalog", "store-status", "board", "tables", "display")) {
			verify(messagingTemplate, org.mockito.Mockito.times(times)).convertAndSend(eq("/topic/" + topic),
					eq("{\"type\":\"RESYNC\",\"ids\":{}}"), anyMap());
		}
	}

	@Test
	void tellsEveryClientTopicToResynchronizeAfterAReconnect() {
		var scripted = new ScriptedListener(DROP, STOP);

		run(scripted);

		verifyResyncSignalSentToEveryClientTopic(1);
		verify(localEvents).publishEvent(new RealtimeSignalReceived("board", "RESYNC", Map.of()));
		verify(localEvents).publishEvent(new RealtimeSignalReceived("catalog", "RESYNC", Map.of()));
	}

	@Test
	void doesNotResynchronizeOnTheFirstConnection() {
		var scripted = new ScriptedListener(STOP);

		run(scripted);

		verifyNoInteractions(messagingTemplate);
	}

	@Test
	void resynchronizesAfterTheListenerCameUpFollowingAFailedFirstConnection() {
		run(new ScriptedListener(STOP).failingFirstConnects(1));

		verifyResyncSignalSentToEveryClientTopic(1);
	}

	@Test
	void aRuntimeExceptionInTheLoopIsLoggedAndTheListenerReconnectsInsteadOfDying() {
		var scripted = new ScriptedListener(CRASH, STOP);

		run(scripted);

		assertThat(scripted.connectsBeforePoll).containsExactly(1, 2);
		verifyResyncSignalSentToEveryClientTopic(1);
	}

	@Test
	void waitsLongerBetweenConsecutiveFailures() {
		var scripted = new ScriptedListener(DROP, CRASH, DROP, STOP);

		run(scripted);

		assertThat(scripted.pauses).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

}
