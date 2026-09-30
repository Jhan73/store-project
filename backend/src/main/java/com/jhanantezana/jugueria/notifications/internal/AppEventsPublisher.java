package com.jhanantezana.jugueria.notifications.internal;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;

import tools.jackson.databind.json.JsonMapper;

// Runs pg_notify on the caller's own transactional connection so PostgreSQL defers delivery until commit.
@Component
class AppEventsPublisher {

	// PostgreSQL rejects a NOTIFY payload over 8000 bytes; our payloads are type + a few IDs, always tiny.
	private static final int MAX_PAYLOAD_BYTES = 8000;

	private static final String CHANNEL = "app_events";

	private final JdbcClient jdbc;

	private final JsonMapper mapper;

	AppEventsPublisher(JdbcClient jdbc, JsonMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	void publish(RealtimeTopic topic, String type, Map<String, String> ids) {
		var payload = payloadFor(topic, type, ids);
		var bytes = payload.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > MAX_PAYLOAD_BYTES) {
			throw new IllegalArgumentException(
					"Realtime signal payload is " + bytes + " bytes, over the NOTIFY 8000-byte limit");
		}
		// Without an active transaction the defer-until-commit guarantee this whole design relies on is gone.
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("AppEventsPublisher.publish must run inside an active transaction");
		}
		// pg_notify(text, text) is the parameterized form; NOTIFY's own grammar only accepts a literal.
		jdbc.sql("select pg_notify(:channel, :payload)").param("channel", CHANNEL).param("payload", payload).query().listOfRows();
	}

	String payloadFor(RealtimeTopic topic, String type, Map<String, String> ids) {
		return mapper.writeValueAsString(new RealtimeSignal(topic.channel(), type, ids));
	}

}
