package com.jhanantezana.jugueria.identity.internal;

import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

public record LoginResult(String accessToken, UUID userId, Role role) {
}
