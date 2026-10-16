package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

record TransferTicketRequest(@NotNull UUID tableId) {
}
