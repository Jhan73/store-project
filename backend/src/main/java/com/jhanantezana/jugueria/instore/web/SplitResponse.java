package com.jhanantezana.jugueria.instore.web;

import java.util.List;

import com.jhanantezana.jugueria.shared.Money;

/** One amount per guest; they add up to what the ticket still owes. */
record SplitResponse(List<Money> parts) {
}
