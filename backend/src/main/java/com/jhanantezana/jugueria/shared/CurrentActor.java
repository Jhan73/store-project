package com.jhanantezana.jugueria.shared;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public interface CurrentActor {

	/** The acting user, or {@code null} for the system and for anonymous requests. */
	@Nullable UUID id();

	/** The acting user's role, or {@code null} for the system and for anonymous requests. */
	@Nullable Role role();

	/** True when no request is being served, as in scheduled jobs and asynchronous listeners. */
	boolean isSystem();

}
