package com.jhanantezana.jugueria.instore.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record SaveWalletProviderRequest(@NotBlank @Size(max = 60) String name) {
}
