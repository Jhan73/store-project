package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.StoreApi;

/**
 * Closing a shift. The count comes first and only then are expected cash and the difference worked out and returned,
 * so a cashier cannot count towards a figure they were shown.
 */
@Service
public class ShiftClosing {

	private final Shifts shifts;

	private final StoreApi store;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	ShiftClosing(Shifts shifts, StoreApi store, ApplicationEventPublisher events, CurrentActor currentActor,
			Clock clock) {
		this.shifts = shifts;
		this.store = store;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * Closes the cashier's own shift with their count. A difference over the threshold needs a note: the request is
	 * answered {@code 409} with the difference and the count is kept, because the cashier has now seen it and must not
	 * change it. That rejection commits, hence {@code noRollbackFor}; nothing else is written after a rejection.
	 * Repeating a close with the count that closed the shift returns the same result.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public ShiftView close(UUID shiftId, Money countedCash, @Nullable String note) {
		var cashier = requireActor();
		var counted = Amounts.require(countedCash, store.currency(), true, "The counted cash");
		var now = Instant.now(clock);
		if (!shifts.lockOpen(shiftId, cashier, now)) {
			return replayOrReject(shiftId, cashier, counted);
		}
		var shift = shifts.find(shiftId).orElseThrow();
		if (shift.counted() != null && shift.counted().compareTo(counted.amount()) != 0) {
			throw new BusinessException(InstoreError.COUNTED_CASH_LOCKED,
					"The count was already shown to the cashier and cannot change");
		}
		var totals = shifts.totals(shiftId, shift.currency());
		var expected = ShiftReconciliation.expectedCash(shift.floatMoney(), totals);
		var difference = ShiftReconciliation.difference(counted, expected);
		var text = TicketService.blankToNull(note);
		if (text == null && ShiftReconciliation.needsNote(difference, store.thresholds().registerDifferenceThreshold())) {
			shifts.storeRevealedCount(shiftId, counted.amount(), now);
			throw new BusinessException(InstoreError.DIFFERENCE_NOTE_REQUIRED,
					"The difference is over the threshold; close again with a note", Map.of("difference", difference));
		}
		shifts.close(shiftId, ShiftCloseKind.COUNTED, counted.amount(), expected.amount(), difference.amount(), text, null,
				cashier, totals, now);
		events.publishEvent(new ShiftClosed(shiftId, shift.cashierId(), false, shift.floatMoney(), counted, expected,
				difference, text, null, totals, cashier, currentActor.role(), now));
		return ShiftService.view(shifts.find(shiftId).orElseThrow());
	}

	/** An ADMIN closes a shift nobody closed; there is no count, so expected cash is returned straight away. */
	@Transactional
	public ShiftView forceClose(UUID shiftId, String reason) {
		var admin = requireActor();
		var text = TicketService.blankToNull(reason);
		if (text == null) {
			throw new BusinessException(InstoreError.INVALID_REASON, "Force-closing a shift needs a reason");
		}
		var now = Instant.now(clock);
		if (!shifts.lockOpen(shiftId, null, now)) {
			var shift = shifts.find(shiftId).orElseThrow(ShiftService::notFound);
			throw new BusinessException(InstoreError.SHIFT_CLOSED, "Shift " + shift.id() + " is already closed");
		}
		var shift = shifts.find(shiftId).orElseThrow();
		var totals = shifts.totals(shiftId, shift.currency());
		var expected = ShiftReconciliation.expectedCash(shift.floatMoney(), totals);
		shifts.close(shiftId, ShiftCloseKind.FORCED, null, expected.amount(), null, null, text, admin, totals, now);
		events.publishEvent(new ShiftClosed(shiftId, shift.cashierId(), true, shift.floatMoney(), null, expected, null, null,
				text, totals, admin, currentActor.role(), now));
		return ShiftService.view(shifts.find(shiftId).orElseThrow());
	}

	private ShiftView replayOrReject(UUID shiftId, UUID cashier, Money counted) {
		var shift = shifts.find(shiftId)
			.filter(found -> found.cashierId().equals(cashier))
			.orElseThrow(ShiftService::notFound);
		if (shift.closeKind() == ShiftCloseKind.COUNTED && shift.counted().compareTo(counted.amount()) == 0) {
			return ShiftService.view(shift);
		}
		throw new BusinessException(InstoreError.SHIFT_CLOSED, "Shift " + shiftId + " is already closed");
	}

	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A shift command needs a signed-in user");
		}
		return id;
	}

}
