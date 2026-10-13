package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;

/** Which drawer a void takes its money from. */
final class Voiding {

	private Voiding() {
	}

	/**
	 * The voider's own open shift, shared until the transaction ends. A cashier without one cannot void; an ADMIN
	 * without one can, and the void then has no drawer behind it ({@code null}).
	 */
	static @Nullable UUID shiftFor(Shifts shifts, UUID actor, @Nullable Role role) {
		var shift = shifts.writableOf(actor);
		if (shift.isPresent()) {
			return shift.get().id();
		}
		if (role == Role.ADMIN) {
			return null;
		}
		throw new BusinessException(InstoreError.NO_OPEN_SHIFT, "A void needs the cashier's open shift");
	}

}
