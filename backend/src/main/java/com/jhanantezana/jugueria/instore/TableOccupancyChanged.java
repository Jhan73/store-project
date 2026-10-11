package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

/** A table's status on the grid changed: a ticket opened on it, left it, or asked for the bill. */
public record TableOccupancyChanged(UUID tableId, Instant occurredAt) {
}
