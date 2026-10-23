package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.StaffChangeGuard;

/** A shift is closed by its cashier, or forced shut by an ADMIN, so its owner must not lose the role meanwhile. */
@Component
class OpenShiftStaffGuard implements StaffChangeGuard {

	private final Shifts shifts;

	OpenShiftStaffGuard(Shifts shifts) {
		this.shifts = shifts;
	}

	@Override
	public void requireCanLeaveRole(UUID userId) {
		if (shifts.findOpenOf(userId).isPresent()) {
			throw new BusinessException(InstoreError.USER_HAS_OPEN_SHIFT,
					"The user has an open register shift; close it first");
		}
	}

}
