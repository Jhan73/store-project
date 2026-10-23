package com.jhanantezana.jugueria.shared;

import java.util.UUID;

/**
 * Lets the modules that hold work done by a staff member veto taking that member out of it. It lives in {@code shared}
 * because the module that asks ({@code identity}) is already reachable from the modules that implement it.
 */
public interface StaffChangeGuard {

	/** Called before a staff account is deactivated or its role changes; throws a {@link BusinessException} to veto. */
	void requireCanLeaveRole(UUID userId);

}
