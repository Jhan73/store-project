package com.jhanantezana.jugueria.notifications;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public interface NotificationsApi {

	void sendStaffSetPasswordEmail(String toEmail, URI setPasswordLink, Instant linkExpiresAt);

	/** Sends one shift summary to one recipient; throws when the transport fails, so callers can retry. */
	void sendShiftSummaryEmail(String toEmail, ShiftSummaryEmail summary);

	/**
	 * Closes the user's real-time (STOMP) sessions on every task. Joins the caller's transaction: the signal goes out
	 * when it commits.
	 */
	void revokeSessions(UUID userId);

}
