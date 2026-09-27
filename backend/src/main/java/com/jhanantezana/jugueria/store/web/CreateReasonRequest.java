package com.jhanantezana.jugueria.store.web;

import com.jhanantezana.jugueria.store.ReasonType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record CreateReasonRequest(@NotNull ReasonType type, @NotBlank String code) {
}
