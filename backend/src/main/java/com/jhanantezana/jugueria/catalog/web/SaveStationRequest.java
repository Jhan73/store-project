package com.jhanantezana.jugueria.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record SaveStationRequest(@NotBlank @Size(max = 80) String name) {
}
