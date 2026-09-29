package com.jhanantezana.jugueria.shared;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;

// Shared by identity (CORS) and notifications (STOMP handshake): one allowlist, never "*", for both.
@ConfigurationProperties("jugueria.web")
@Validated
public record WebOriginsProperties(@NotEmpty List<String> allowedOrigins) {
}
