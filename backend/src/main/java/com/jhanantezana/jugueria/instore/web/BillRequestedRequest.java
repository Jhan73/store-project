package com.jhanantezana.jugueria.instore.web;

/** Whether the table asked for the bill; an absolute value, so sending it twice is harmless. */
record BillRequestedRequest(boolean requested) {
}
