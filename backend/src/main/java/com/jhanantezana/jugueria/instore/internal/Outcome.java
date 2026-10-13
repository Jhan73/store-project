package com.jhanantezana.jugueria.instore.internal;

/** What an idempotent command answered, and whether that answer was stored by an earlier try of the same request. */
public record Outcome<T>(T value, boolean replayed) {
}
