package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;

/** Which drawer a void takes its money from: never the voider's own for a payment taken in someone else's shift. */
final class Voiding {

	private Voiding() {
	}

	/**
	 * A cashier voids only a payment taken in their own current open shift. An ADMIN's void goes to the shift that took
	 * the payment while it is open, and to no drawer ({@code null}) once it is closed. The shift row is shared until
	 * the transaction ends; a shift whose count was already shown takes no more changes.
	 */
	static @Nullable UUID shiftFor(Shifts shifts, UUID actor, @Nullable Role role, Payments.PaymentRow payment) {
		if (role == Role.ADMIN) {
			var taken = shifts.share(payment.shiftId());
			if (taken.isEmpty() || taken.get().status() != ShiftStatus.OPEN) {
				return null;
			}
			if (taken.get().countRevealed()) {
				throw new BusinessException(InstoreError.SHIFT_CLOSING, "The shift that took the payment is being closed");
			}
			return taken.get().id();
		}
		var own = shifts.writableOf(actor)
			.orElseThrow(() -> new BusinessException(InstoreError.NO_OPEN_SHIFT, "A void needs the cashier's open shift"));
		if (!own.id().equals(payment.shiftId())) {
			throw new BusinessException(InstoreError.SALE_VOID_NOT_PERMITTED,
					"A cashier voids only a payment taken in their own open shift");
		}
		return own.id();
	}

}
