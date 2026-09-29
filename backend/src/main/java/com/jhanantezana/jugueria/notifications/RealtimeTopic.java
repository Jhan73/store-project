package com.jhanantezana.jugueria.notifications;

/** The STOMP destinations producers may signal; each maps to a fixed {@code /topic/**} destination. */
public enum RealtimeTopic {

	CATALOG("catalog"), STORE_STATUS("store-status");

	private final String channel;

	RealtimeTopic(String channel) {
		this.channel = channel;
	}

	public String destination() {
		return "/topic/" + channel;
	}

	// Used to build the NOTIFY payload; public because internal/ sits in a different Java package.
	public String channel() {
		return channel;
	}

}
