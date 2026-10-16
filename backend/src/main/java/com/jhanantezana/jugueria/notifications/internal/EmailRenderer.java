package com.jhanantezana.jugueria.notifications.internal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

// Templates are classpath:notifications/emails/<message>_<language>.txt; the first line is "Subject: ...".
@Component
class EmailRenderer {

	private static final String LOCATION = "notifications/emails/";

	private static final String EXTENSION = ".txt";

	private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es");

	private static final String SUBJECT_PREFIX = "Subject:";

	private final SpringTemplateEngine engine = new SpringTemplateEngine();

	EmailRenderer() {
		var resolver = new ClassLoaderTemplateResolver();
		resolver.setTemplateMode(TemplateMode.TEXT);
		resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resolver.setPrefix(LOCATION);
		resolver.setSuffix(EXTENSION);
		engine.setTemplateResolver(resolver);
	}

	RenderedEmail render(String message, Locale locale, Map<String, Object> variables) {
		var template = templateFor(message, locale);
		var context = new Context(locale);
		context.setVariables(variables);
		var parts = engine.process(template, context).split("\\R", 2);
		var subject = parts[0].replaceFirst("^" + SUBJECT_PREFIX, "").strip();
		var body = parts.length > 1 ? parts[1].strip() : "";
		return new RenderedEmail(subject, body);
	}

	private static String templateFor(String message, Locale locale) {
		var requested = message + "_" + locale.getLanguage();
		if (exists(requested)) {
			return requested;
		}
		var fallback = message + "_" + DEFAULT_LOCALE.getLanguage();
		if (exists(fallback)) {
			return fallback;
		}
		throw new IllegalStateException("Missing email template for message " + message);
	}

	private static boolean exists(String template) {
		return new ClassPathResource(LOCATION + template + EXTENSION).exists();
	}

}
