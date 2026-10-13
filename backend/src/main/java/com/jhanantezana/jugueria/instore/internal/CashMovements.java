package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.CashMovementRecorded;
import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.StoreApi;

/** Cash in and cash out of the open shift's drawer (a supplier paid from it, change brought in). */
@Service
public class CashMovements {

	private static final String OTHER_REASON = "other";

	private static final int MAX_REFERENCE = 100;

	private final Shifts shifts;

	private final CashMovementLog log;

	private final StoreApi store;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	CashMovements(Shifts shifts, CashMovementLog log, StoreApi store, ApplicationEventPublisher events,
			CurrentActor currentActor, Clock clock) {
		this.shifts = shifts;
		this.log = log;
		this.store = store;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/**
	 * Cash out names an active cash-out reason ("Other" needs a note); cash in has no reason list, so it needs a note
	 * saying where the money came from.
	 */
	@Transactional
	public CashMovementView record(UUID shiftId, CashMovementType type, Money amount, @Nullable String reasonCode,
			@Nullable String note, @Nullable String reference) {
		var actor = requireActor();
		var shift = shifts.share(shiftId).filter(found -> found.cashierId().equals(actor)).orElseThrow(ShiftService::notFound);
		if (shift.status() != ShiftStatus.OPEN) {
			throw new BusinessException(InstoreError.SHIFT_CLOSED, "Shift " + shiftId + " is closed");
		}
		if (shift.countRevealed()) {
			throw new BusinessException(InstoreError.SHIFT_CLOSING, "Shift " + shiftId + " is being closed");
		}
		Amounts.require(amount, store.currency(), false, "The amount");
		var text = TicketService.blankToNull(note);
		var ref = TicketService.blankToNull(reference);
		if (ref != null && ref.length() > MAX_REFERENCE) {
			throw new BusinessException(InstoreError.INVALID_CASH_MOVEMENT, "The reference is too long");
		}
		var reason = type == CashMovementType.OUT ? requireOutReason(reasonCode, text) : requireInNote(reasonCode, text);
		var now = Instant.now(clock);
		var id = Ids.newId();
		log.insert(id, shiftId, type, amount, reason, text, ref, actor, now);
		events.publishEvent(new CashMovementRecorded(id, shiftId, type, amount, reason, text, ref, actor,
				currentActor.role(), now));
		return log.require(id);
	}

	@Transactional(readOnly = true)
	public List<CashMovementView> list(UUID shiftId) {
		var actor = requireActor();
		shifts.find(shiftId)
			.filter(found -> found.cashierId().equals(actor) || currentActor.role() == Role.ADMIN)
			.orElseThrow(ShiftService::notFound);
		return log.ofShift(shiftId);
	}

	@Transactional(readOnly = true)
	public CashMovementView get(UUID shiftId, UUID movementId) {
		return list(shiftId).stream()
			.filter(movement -> movement.id().equals(movementId))
			.findFirst()
			.orElseThrow(() -> new BusinessException(InstoreError.CASH_MOVEMENT_NOT_FOUND, "Cash movement not found"));
	}

	private @Nullable String requireInNote(@Nullable String reasonCode, @Nullable String note) {
		if (reasonCode != null && !reasonCode.isBlank()) {
			throw new BusinessException(InstoreError.INVALID_CASH_MOVEMENT, "Cash in has no reason list; explain it in the note");
		}
		if (note == null) {
			throw new BusinessException(InstoreError.INVALID_CASH_MOVEMENT, "Cash in needs a note saying where it comes from");
		}
		return null;
	}

	private String requireOutReason(@Nullable String reasonCode, @Nullable String note) {
		if (reasonCode == null || reasonCode.isBlank()) {
			throw new BusinessException(InstoreError.INVALID_REASON, "Cash out needs a reason");
		}
		var reason = store.reasons(ReasonType.CASH_OUT)
			.stream()
			.filter(candidate -> candidate.active() && candidate.code().equalsIgnoreCase(reasonCode.trim()))
			.findFirst()
			.orElseThrow(() -> new BusinessException(InstoreError.INVALID_REASON, "The reason is not an active cash-out reason"));
		if (OTHER_REASON.equalsIgnoreCase(reason.code()) && note == null) {
			throw new BusinessException(InstoreError.INVALID_REASON, "The reason \"Other\" needs a note");
		}
		return reason.code();
	}

	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A cash movement needs a signed-in user");
		}
		return id;
	}

}
