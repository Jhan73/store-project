package com.jhanantezana.jugueria.notifications;

import java.util.Set;

import com.jhanantezana.jugueria.shared.Role;

/** The STOMP destinations producers may signal; each maps to a fixed {@code /topic/**} destination. */
public enum RealtimeTopic {

	CATALOG("catalog", true), STORE_STATUS("store-status", true),
	BOARD("board", true, Role.SERVER, Role.CASHIER, Role.ADMIN),
	TABLES("tables", true, Role.SERVER, Role.CASHIER, Role.ADMIN),
	DISPLAY("display", true, Role.SERVER, Role.CASHIER, Role.ADMIN),

	/** Tells every task to act on a user's sessions; nobody subscribes to it. */
	SESSIONS("sessions", false);

	private final String channel;

	private final boolean subscribable;

	private final Set<Role> subscriberRoles;

	RealtimeTopic(String channel, boolean subscribable, Role... subscriberRoles) {
		this.channel = channel;
		this.subscribable = subscribable;
		this.subscriberRoles = Set.of(subscriberRoles);
	}

	public String destination() {
		return "/topic/" + channel;
	}

	// Used to build the NOTIFY payload; public because internal/ sits in a different Java package.
	public String channel() {
		return channel;
	}

	/** Whether clients may subscribe at all. */
	public boolean subscribable() {
		return subscribable;
	}

	/** The roles that may subscribe; empty means anyone, anonymous included. */
	public Set<Role> subscriberRoles() {
		return subscriberRoles;
	}

}
