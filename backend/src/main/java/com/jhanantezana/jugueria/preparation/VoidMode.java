package com.jhanantezana.jugueria.preparation;

/** How far a void may reach into the preparation of a line; the caller maps the actor's role to one of these. */
public enum VoidMode {

	/** Only a line nobody has finished yet (a SERVER's void). */
	PENDING_ONLY,

	/** Also a finished line, which then counts as waste (a CASHIER's or ADMIN's void). */
	ALLOW_READY_AS_WASTE

}
