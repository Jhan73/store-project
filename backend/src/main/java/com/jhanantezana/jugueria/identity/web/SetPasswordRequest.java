package com.jhanantezana.jugueria.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Length only, no composition rules: NIST 800-63B favors length over forced complexity.
record SetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 12, max = 100) String newPassword) {
}
