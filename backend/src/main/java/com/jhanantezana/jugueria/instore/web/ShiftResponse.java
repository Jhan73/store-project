package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.ShiftCloseKind;
import com.jhanantezana.jugueria.instore.internal.ShiftStatus;
import com.jhanantezana.jugueria.instore.internal.ShiftView;
import com.jhanantezana.jugueria.shared.Money;

/** While the shift is open only the first five fields are filled: the figures appear once it is closed. */
record ShiftResponse(UUID id, UUID cashierId, ShiftStatus status, Money openingFloat, Instant openedAt,
		@Nullable Instant closedAt, @Nullable ShiftCloseKind closeKind, @Nullable Money countedCash,
		@Nullable Money expectedCash, @Nullable Money difference, @Nullable String differenceNote,
		@Nullable String forceReason, @Nullable UUID closedBy, @Nullable ShiftTotalsResponse totals) {

	static ShiftResponse from(ShiftView shift) {
		return new ShiftResponse(shift.id(), shift.cashierId(), shift.status(), shift.openingFloat(), shift.openedAt(),
				shift.closedAt(), shift.closeKind(), shift.countedCash(), shift.expectedCash(), shift.difference(),
				shift.differenceNote(), shift.forceReason(), shift.closedBy(),
				shift.totals() == null ? null : ShiftTotalsResponse.from(shift.totals()));
	}

}
