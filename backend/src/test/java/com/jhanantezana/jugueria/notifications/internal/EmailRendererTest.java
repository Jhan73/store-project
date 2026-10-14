package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EmailRendererTest {

	final EmailRenderer renderer = new EmailRenderer();

	@Test
	void splitsTheSubjectLineFromTheBodyAndFillsTheVariables() {
		var rendered = renderer.render("renderer-probe", Locale.forLanguageTag("es"), Map.of("name", "Ana"));

		assertThat(rendered.subject()).isEqualTo("Hola Ana");
		assertThat(rendered.body()).isEqualTo("Cuerpo para Ana.");
	}

	@Test
	void fallsBackToTheSpanishTemplateWhenTheLocaleHasNone() {
		var rendered = renderer.render("renderer-probe", Locale.forLanguageTag("fr"), Map.of("name", "Ana"));

		assertThat(rendered.subject()).isEqualTo("Hola Ana");
	}

	@Test
	void picksTheTemplateOfTheRequestedLocale() {
		var rendered = renderer.render("renderer-probe", Locale.ENGLISH, Map.of("name", "Ana"));

		assertThat(rendered.subject()).isEqualTo("Hello Ana");
	}

	@Test
	void keepsSpecialCharactersOfUnescapedInlining() {
		var rendered = renderer.render("renderer-probe", Locale.forLanguageTag("es"),
				Map.of("name", "https://x.test/?a=1&b=2"));

		assertThat(rendered.body()).contains("https://x.test/?a=1&b=2");
	}

	@Test
	void failsLoudlyWhenNoTemplateExists() {
		assertThatThrownBy(() -> renderer.render("missing", Locale.forLanguageTag("es"), Map.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("missing");
	}

}
