package com.jhanantezana.jugueria.store;

import java.util.UUID;

public record ReasonView(UUID id, ReasonType type, String code, boolean active) {
}
