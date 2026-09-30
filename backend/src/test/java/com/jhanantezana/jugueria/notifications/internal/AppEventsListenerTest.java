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

import java.time.Duration;
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
	void isNotRunningUntilStarted() {
		assertThat(listener.isRunning()).isFalse();
	}

}
