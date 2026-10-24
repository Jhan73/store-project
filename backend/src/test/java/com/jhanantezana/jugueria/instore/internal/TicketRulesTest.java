package com.jhanantezana.jugueria.instore.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.preparation.VoidMode;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

class TicketRulesTest {

	static final Currency PEN = Currency.getInstance("PEN");

	@Test
	void aServerMayVoidOnlyWhatIsNotReadyAndCashiersAndAdminsMayVoidReadyLinesAsWaste() {
		assertThat(TicketRules.voidModeFor(Role.SERVER)).isEqualTo(VoidMode.PENDING_ONLY);
		assertThat(TicketRules.voidModeFor(Role.CASHIER)).isEqualTo(VoidMode.ALLOW_READY_AS_WASTE);
		assertThat(TicketRules.voidModeFor(Role.ADMIN)).isEqualTo(VoidMode.ALLOW_READY_AS_WASTE);
	}

	@Test
	void aCustomerHasNoVoidMode() {
		assertThatThrownBy(() -> TicketRules.voidModeFor(Role.CUSTOMER)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theFirstRoundKeepsTheTicketNumberAndLaterRoundsAreNumbered() {
		assertThat(TicketRules.boardNumber(12, 1, false)).isEqualTo("T12");
		assertThat(TicketRules.boardNumber(12, 2, false)).isEqualTo("T12-2");
		assertThat(TicketRules.boardNumber(12, 3, false)).isEqualTo("T12-3");
	}

	@Test
	void aQuickSaleIsNumberedWithItsOwnPrefix() {
		assertThat(TicketRules.boardNumber(12, 1, true)).isEqualTo("Q12");
		assertThat(TicketRules.boardNumber(12, 1, false)).isEqualTo("T12");
		assertThat(TicketRules.boardNumber(12, 2, true)).isEqualTo("Q12-2");
	}

	@Test
	void theTotalCountsOnlyLinesThatAreNeitherVoidedNorComped() {
		var lines = List.of(line(TicketLineState.UNSENT, "5.00", 2), line(TicketLineState.SENT, "4.50", 1),
				line(TicketLineState.VOIDED, "9.00", 3), line(TicketLineState.COMPED, "7.00", 1));

		assertThat(TicketRules.total(lines, PEN)).isEqualTo(Money.of("14.50", PEN));
	}

	@Test
	void theTotalOfNoLinesIsZero() {
		assertThat(TicketRules.total(List.of(), PEN)).isEqualTo(Money.zero(PEN));
	}

	@Test
	void joinsTheChosenOptionNamesForTheBoardAndCapsTheirLength() {
		var options = List.of(new ChosenOption(java.util.UUID.randomUUID(), "Large", Money.of("3.00", PEN)),
				new ChosenOption(java.util.UUID.randomUUID(), "Chia", Money.zero(PEN)));

		assertThat(TicketRules.modifiersText(options)).isEqualTo("Large, Chia");
		assertThat(TicketRules.modifiersText(List.of())).isNull();
		var many = java.util.stream.IntStream.range(0, 200)
			.mapToObj(i -> new ChosenOption(java.util.UUID.randomUUID(), "Option number " + i, Money.zero(PEN)))
			.toList();
		assertThat(TicketRules.modifiersText(many)).hasSize(500).endsWith("…");
	}

	private static TicketLineView line(TicketLineState state, String unitPrice, int quantity) {
		return new TicketLineView(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Juice", quantity,
				Money.of(unitPrice, PEN), List.of(), null, false, state, null, false, null, null, null, null);
	}

}
