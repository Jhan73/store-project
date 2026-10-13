package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.shared.Money;

/** Register commands a retry could repeat; each registers its Idempotency-Key before doing anything else. */
@Service
public class ShiftCommands {

	private static final int CREATED = 201;

	private final ShiftService shifts;

	private final CashMovements movements;

	private final IdempotentRun idempotent;

	ShiftCommands(ShiftService shifts, CashMovements movements, IdempotentRun idempotent) {
		this.shifts = shifts;
		this.movements = movements;
		this.idempotent = idempotent;
	}

	@Transactional
	public Outcome<ShiftView> open(UUID actorId, UUID key, String requestHash, Money openingFloat) {
		return idempotent.once(actorId, key, requestHash, CREATED, ShiftView.class, () -> shifts.open(openingFloat));
	}

	@Transactional
	public Outcome<CashMovementView> recordMovement(UUID actorId, UUID key, String requestHash, UUID shiftId,
			CashMovementType type, Money amount, @Nullable String reasonCode, @Nullable String note,
			@Nullable String reference) {
		return idempotent.once(actorId, key, requestHash, CREATED, CashMovementView.class,
				() -> movements.record(shiftId, type, amount, reasonCode, note, reference));
	}

}
