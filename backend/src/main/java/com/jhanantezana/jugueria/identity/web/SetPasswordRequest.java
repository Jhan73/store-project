package com.jhanantezana.jugueria.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No complexity rules, length only (tech-spec §7.1 gap filled here): NIST 800-63B favors length over composition.
record SetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 12, max = 100) String newPassword) {
}
