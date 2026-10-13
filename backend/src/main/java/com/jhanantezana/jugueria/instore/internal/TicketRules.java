package com.jhanantezana.jugueria.instore.internal;

import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.BoardLineIntake;
import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

/** The ticket rules that need neither the database nor Spring. */
final class TicketRules {

	private static final String ELLIPSIS = "…";

	private TicketRules() {
	}

	/** How far into the kitchen's work a role's void may reach. */
	static VoidMode voidModeFor(Role role) {
		return switch (role) {
			case SERVER -> VoidMode.PENDING_ONLY;
			case CASHIER, ADMIN -> VoidMode.ALLOW_READY_AS_WASTE;
			case CUSTOMER -> throw new IllegalArgumentException("A customer cannot void ticket lines");
		};
	}

	/**
	 * The number a card shows on the board: the ticket's own for the first round, then suffixed with the round. A quick
	 * sale carries a {@code Q} instead of the {@code T}, so staff tell a counter sale from a table at a glance.
	 */
	static String boardNumber(long ticketNumber, int round, boolean quickSale) {
		var prefix = quickSale ? "Q" : "T";
		return round <= 1 ? prefix + ticketNumber : prefix + ticketNumber + "-" + round;
	}

	/** What the ticket costs now: voided lines are not charged. */
	static Money total(List<TicketLineView> lines, Currency currency) {
		var total = Money.zero(currency);
		for (var line : lines) {
			if (line.state() == TicketLineState.UNSENT || line.state() == TicketLineState.SENT) {
				total = total.plus(line.lineTotal());
			}
		}
		return total;
	}

	static @Nullable String modifiersText(List<ChosenOption> options) {
		if (options.isEmpty()) {
			return null;
		}
		var text = options.stream().map(ChosenOption::name).collect(Collectors.joining(", "));
		var max = BoardLineIntake.MAX_MODIFIERS_TEXT;
		return text.length() <= max ? text : text.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
	}

}
