package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import tools.jackson.databind.json.JsonMapper;

class AppEventsListenerTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

	private final JdbcConnectionDetails connectionDetails = mock(JdbcConnectionDetails.class);

	private final NotificationsProperties.Ses ses = new NotificationsProperties.Ses("us-east-1", Duration.ofSeconds(10),
			Duration.ofSeconds(5));

	private final NotificationsProperties properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com",
			List.of(), ses, "jugueria-backend");

	private final AppEventsListener listener = new AppEventsListener(connectionDetails, messagingTemplate, mapper,
			properties);

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
