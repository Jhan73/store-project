package com.jhanantezana.jugueria.notifications.internal;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Public within internal/ so the smtp, ses, and LISTEN-connection code can read it.
@ConfigurationProperties("jugueria.notifications")
@Validated
public record NotificationsProperties(@NotBlank String senderAddress, List<String> recipientAllowlist,
		@Valid @NotNull Ses ses,
		// Distinguishes each task's LISTEN connection in pg_stat_activity; overridden per instance in tests.
		@NotBlank @DefaultValue("jugueria-backend") String listenerApplicationName) {

	// Blank entries (e.g. an unset env var bound through a ":" default) must never become a one-element allowlist.
	public NotificationsProperties {
		recipientAllowlist = recipientAllowlist == null ? List.of()
				: recipientAllowlist.stream().filter(entry -> !entry.isBlank()).toList();
	}

	public record Ses(@NotBlank String region, @NotNull Duration apiCallTimeout, @NotNull Duration apiCallAttemptTimeout) {
	}

}
