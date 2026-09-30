package com.jhanantezana.jugueria.catalog.web;

import jakarta.validation.constraints.NotNull;

record AvailabilityRequest(@NotNull Boolean available) {
}
