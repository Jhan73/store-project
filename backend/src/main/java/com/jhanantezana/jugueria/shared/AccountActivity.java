package com.jhanantezana.jugueria.shared;

import java.util.UUID;

/**
 * Whether a user account may still act. It lives in {@code shared} because {@code identity}, which implements it,
 * already depends on {@code notifications}, the module that asks while it holds long-lived connections.
 */
public interface AccountActivity {

	/** False for a deactivated account and for one that does not exist. */
	boolean isActive(UUID userId);

}
