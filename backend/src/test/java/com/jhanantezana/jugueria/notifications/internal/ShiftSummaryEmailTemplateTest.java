package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.notifications.ShiftSummaryEmail;
import com.jhanantezana.jugueria.shared.Money;

class ShiftSummaryEmailTemplateTest {

	static final Currency PEN = Currency.getInstance("PEN");

	static final Instant CLOSED_AT = Instant.parse("2026-10-13T02:30:00Z");

	final ShiftSummaryEmailTemplate template = new ShiftSummaryEmailTemplate(new EmailRenderer());

	@Test
	void rendersTheCountedShiftWithItsDifferenceAndNote() {
		var summary = summary(false, Money.of("1234.50", PEN), Money.of("-5.00", PEN), "Short on change");

		var rendered = template.render(summary);

		assertThat(rendered.subject()).contains("cajero@jugueria.pe");
		assertThat(rendered.body()).contains("12/10/2026 21:30")
			.contains("PEN 1,234.50")
			.contains("PEN -5.00")
			.contains("Short on change")
			.contains("Efectivo")
			.contains("Tarjeta");
	}

	@Test
	void rendersAForcedCloseWithItsReasonInsteadOfACount() {
		var summary = summary(true, null, null, "Cashier left");

		var rendered = template.render(summary);

		assertThat(rendered.body()).contains("Cashier left").doesNotContain("contado");
	}

	static ShiftSummaryEmail summary(boolean forced, Money counted, Money difference, String note) {
		return new ShiftSummaryEmail("cajero@jugueria.pe", CLOSED_AT, ZoneId.of("America/Lima"), forced,
				Money.of("100.00", PEN), counted, Money.of("1239.50", PEN), difference, note, Money.of("900.00", PEN),
				Money.of("10.00", PEN), Money.of("0.00", PEN), Money.of("20.00", PEN), Money.of("5.00", PEN),
				List.of(new ShiftSummaryEmail.MethodTotal("CASH", Money.of("900.00", PEN), Money.of("0.00", PEN),
						Money.of("900.00", PEN)),
						new ShiftSummaryEmail.MethodTotal("CARD", Money.of("300.00", PEN), Money.of("0.00", PEN),
								Money.of("300.00", PEN))));
	}

}
