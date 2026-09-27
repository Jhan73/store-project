package com.jhanantezana.jugueria.notifications;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public interface NotificationsApi {

	void sendStaffSetPasswordEmail(String toEmail, URI setPasswordLink, Instant linkExpiresAt);

	/**
	 * Signals a realtime change on the given topic. Call this from inside the same transaction as the
	 * change it announces: the underlying {@code NOTIFY} only reaches other instances once that
	 * transaction commits (tech-spec §4.4). {@code ids} carries only IDs, never entities or personal data.
	 */
	void publish(RealtimeTopic topic, String type, Map<String, String> ids);

}
