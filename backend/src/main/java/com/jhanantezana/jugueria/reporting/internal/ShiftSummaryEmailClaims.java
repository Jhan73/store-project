package com.jhanantezana.jugueria.reporting.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Each statement commits on its own. A recipient is claimed before the send, marked sent after it, and the claim is
 * given back if the send fails; a claim older than the lease belongs to a delivery that died and can be taken over.
 */
@Component
class ShiftSummaryEmailClaims {

	enum Claim {

		CLAIMED, ALREADY_SENT, BUSY

	}

	private final JdbcClient jdbc;

	ShiftSummaryEmailClaims(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Claim claim(UUID shiftId, String recipient, Instant now, Duration lease) {
		var taken = jdbc.sql("""
				INSERT INTO reporting.shift_summary_delivery AS d (shift_id, recipient, status, claimed_at)
				VALUES (:shift, :recipient, 'CLAIMED', :now)
				ON CONFLICT (shift_id, recipient) DO UPDATE SET claimed_at = :now
				WHERE d.status = 'CLAIMED' AND d.claimed_at < :staleBefore
				""")
			.param("shift", shiftId)
			.param("recipient", recipient)
			.param("now", utc(now))
			.param("staleBefore", utc(now.minus(lease)))
			.update();
		if (taken == 1) {
			return Claim.CLAIMED;
		}
		var sent = jdbc.sql("SELECT count(*) FROM reporting.shift_summary_delivery "
				+ "WHERE shift_id = :shift AND recipient = :recipient AND status = 'SENT'")
			.param("shift", shiftId)
			.param("recipient", recipient)
			.query(Long.class)
			.single();
		return sent == 1 ? Claim.ALREADY_SENT : Claim.BUSY;
	}

	void markSent(UUID shiftId, String recipient, Instant now) {
		jdbc.sql("""
				UPDATE reporting.shift_summary_delivery SET status = 'SENT', sent_at = :now
				WHERE shift_id = :shift AND recipient = :recipient AND status = 'CLAIMED'
				""").param("shift", shiftId).param("recipient", recipient).param("now", utc(now)).update();
	}

	void release(UUID shiftId, String recipient) {
		jdbc.sql("DELETE FROM reporting.shift_summary_delivery "
				+ "WHERE shift_id = :shift AND recipient = :recipient AND status = 'CLAIMED'")
			.param("shift", shiftId)
			.param("recipient", recipient)
			.update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
