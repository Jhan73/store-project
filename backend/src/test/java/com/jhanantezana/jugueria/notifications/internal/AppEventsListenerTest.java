package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import tools.jackson.databind.json.JsonMapper;

class AppEventsListenerTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

	private final JdbcConnectionDetails connectionDetails = mock(JdbcConnectionDetails.class);

	private final AppEventsListener listener = new AppEventsListener(connectionDetails, messagingTemplate, mapper);

	@Test
	void forwardsANotificationToItsTopicDestinationWithoutTheTopicField() {
		listener.forward("{\"topic\":\"store-status\",\"type\":\"STORE_SETTINGS_CHANGED\",\"ids\":{\"settingsId\":\"abc\"}}");

		verify(messagingTemplate).convertAndSend(eq("/topic/store-status"),
				eq("{\"type\":\"STORE_SETTINGS_CHANGED\",\"ids\":{\"settingsId\":\"abc\"}}"), anyMap());
	}

	@Test
	void ignoresAMalformedNotificationInsteadOfThrowing() {
		listener.forward("not json");

		verifyNoInteractions(messagingTemplate);
	}

	@Test
	void isNotRunningUntilStarted() {
		assertThat(listener.isRunning()).isFalse();
	}

}
