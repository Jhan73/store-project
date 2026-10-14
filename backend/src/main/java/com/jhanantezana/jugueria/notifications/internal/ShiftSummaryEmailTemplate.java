package com.jhanantezana.jugueria.notifications.internal;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.ShiftSummaryEmail;

@Component
class ShiftSummaryEmailTemplate {

	private static final String MESSAGE = "shift-summary";

	private final EmailRenderer renderer;

	ShiftSummaryEmailTemplate(EmailRenderer renderer) {
		this.renderer = renderer;
	}

	RenderedEmail render(ShiftSummaryEmail summary) {
		var closedAt = summary.closedAt().atZone(summary.zone());
		return renderer.render(MESSAGE, Locale.forLanguageTag("es"), Map.of("summary", summary, "closedAt", closedAt));
	}

}
