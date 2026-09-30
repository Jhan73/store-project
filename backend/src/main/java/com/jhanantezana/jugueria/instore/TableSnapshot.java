package com.jhanantezana.jugueria.instore;

import org.jspecify.annotations.Nullable;

public record TableSnapshot(String name, @Nullable String area, int displayOrder) {
}
