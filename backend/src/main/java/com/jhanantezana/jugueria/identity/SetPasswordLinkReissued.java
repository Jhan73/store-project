package com.jhanantezana.jugueria.identity;

import java.time.Instant;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Role;

// Never carries the token or the link itself: only that a new credential was issued, and by whom.
public record SetPasswordLinkReissued(UUID userId, UUID actorId, Role actorRole, Instant occurredAt) {
}
