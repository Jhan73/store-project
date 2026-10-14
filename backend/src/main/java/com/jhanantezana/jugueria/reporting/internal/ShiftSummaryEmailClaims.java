package com.jhanantezana.jugueria.reporting.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Each statement commits on its own: the claim is taken before the send and given back if the send fails. */
@Component
class ShiftSummaryEmailClaims {

	private final JdbcClient jdbc;

	ShiftSummaryEmailClaims(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean claim(UUID shiftId, Instant now) {
		return jdbc.sql("""
				INSERT INTO reporting.shift_summary_email (shift_id, sent_at) VALUES (:shift, :now)
				ON CONFLICT (shift_id) DO NOTHING
				""").param("shift", shiftId).param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update() == 1;
	}

	void release(UUID shiftId) {
		jdbc.sql("DELETE FROM reporting.shift_summary_email WHERE shift_id = :shift").param("shift", shiftId).update();
	}

}
