package com.jhanantezana.jugueria.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RenameStaffRequest(@NotBlank @Size(max = 60) String username) {
}
