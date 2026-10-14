package com.jhanantezana.jugueria.reporting.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.instore.ShiftOpened;

import tools.jackson.databind.json.JsonMapper;

/** The opened and closed halves of a summary arrive as separate events in any order; each writes only its columns. */
@Component
class ShiftSummaries {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ShiftSummaries(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	void opened(ShiftOpened event) {
		jdbc.sql("""
				INSERT INTO reporting.shift_summary (shift_id, cashier_id, currency, opened_at, opening_float_amount)
				VALUES (:shift, :cashier, :currency, :openedAt, :float)
				ON CONFLICT (shift_id) DO UPDATE SET opened_at = EXCLUDED.opened_at
				""")
			.param("shift", event.shiftId())
			.param("cashier", event.cashierId())
			.param("currency", event.openingFloat().currency().getCurrencyCode())
			.param("openedAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
			.param("float", event.openingFloat().amount())
			.update();
	}

	void closed(ShiftClosed event) {
		var totals = event.totals();
		var counted = event.countedCash();
		var difference = event.difference();
		jdbc.sql("""
				INSERT INTO reporting.shift_summary (shift_id, cashier_id, currency, opening_float_amount, closed_at,
					closed_by, forced, counted_amount, expected_amount, difference_amount, note, cash_received_amount,
					change_given_amount, voided_cash_amount, cash_in_amount, cash_out_amount, by_method)
				VALUES (:shift, :cashier, :currency, :float, :closedAt, :closedBy, :forced, :counted, :expected,
					:difference, :note, :received, :change, :voided, :cashIn, :cashOut, CAST(:byMethod AS jsonb))
				ON CONFLICT (shift_id) DO UPDATE SET closed_at = EXCLUDED.closed_at, closed_by = EXCLUDED.closed_by,
					forced = EXCLUDED.forced, counted_amount = EXCLUDED.counted_amount,
					expected_amount = EXCLUDED.expected_amount, difference_amount = EXCLUDED.difference_amount,
					note = EXCLUDED.note, cash_received_amount = EXCLUDED.cash_received_amount,
					change_given_amount = EXCLUDED.change_given_amount, voided_cash_amount = EXCLUDED.voided_cash_amount,
					cash_in_amount = EXCLUDED.cash_in_amount, cash_out_amount = EXCLUDED.cash_out_amount,
					by_method = EXCLUDED.by_method
				""")
			.param("shift", event.shiftId())
			.param("cashier", event.cashierId())
			.param("currency", event.openingFloat().currency().getCurrencyCode())
			.param("float", event.openingFloat().amount())
			.param("closedAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
			.param("closedBy", event.actorId())
			.param("forced", event.forced())
			.param("counted", counted == null ? null : counted.amount())
			.param("expected", event.expectedCash().amount())
			.param("difference", difference == null ? null : difference.amount())
			.param("note", event.forced() ? event.forceReason() : event.differenceNote())
			.param("received", totals.cashReceived().amount())
			.param("change", totals.changeGiven().amount())
			.param("voided", totals.voidedCash().amount())
			.param("cashIn", totals.cashIn().amount())
			.param("cashOut", totals.cashOut().amount())
			.param("byMethod", json.writeValueAsString(totals.byMethod()))
			.update();
	}

}
