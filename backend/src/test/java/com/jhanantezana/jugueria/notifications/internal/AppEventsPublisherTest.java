package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;

import tools.jackson.databind.json.JsonMapper;

class AppEventsPublisherTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void buildsAPayloadWithOnlyTypeAndIds() {
		var jdbc = mock(JdbcClient.class);
		var publisher = new AppEventsPublisher(jdbc, mapper);

		var payload = publisher.payloadFor(RealtimeTopic.STORE_STATUS, "STORE_SETTINGS_CHANGED",
				Map.of("settingsId", "abc-123"));

		assertThat(payload).isEqualTo(
				"{\"topic\":\"store-status\",\"type\":\"STORE_SETTINGS_CHANGED\",\"ids\":{\"settingsId\":\"abc-123\"}}");
	}

	@Test
	void rejectsAPayloadOverTheNotifyByteLimitWithoutTouchingTheDatabase() {
		var jdbc = mock(JdbcClient.class);
		var publisher = new AppEventsPublisher(jdbc, mapper);
		var hugeIds = new HashMap<String, String>();
		hugeIds.put("id", "x".repeat(9000));

		assertThatThrownBy(() -> publisher.publish(RealtimeTopic.CATALOG, "PRODUCT_AVAILABILITY_CHANGED", hugeIds))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(jdbc);
	}

}
