package com.jhanantezana.jugueria.catalog;

import java.util.UUID;

public record CategorySnapshot(String name, int displayOrder, UUID stationId, boolean active) {
}
