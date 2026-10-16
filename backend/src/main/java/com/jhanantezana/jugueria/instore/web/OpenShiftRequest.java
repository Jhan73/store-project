package com.jhanantezana.jugueria.instore.web;

import com.jhanantezana.jugueria.shared.Money;

import jakarta.validation.constraints.NotNull;

record OpenShiftRequest(@NotNull Money openingFloat) {
}
