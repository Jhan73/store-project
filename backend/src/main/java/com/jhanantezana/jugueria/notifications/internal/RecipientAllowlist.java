package com.jhanantezana.jugueria.notifications.internal;

import java.util.List;
import java.util.Locale;

// Public within internal/ so the ses sub-package can use it. Empty entries mean unrestricted (prod);
// a "@domain" entry matches any local part at that domain.
public record RecipientAllowlist(List<String> entries) {

	public boolean allows(String email) {
		if (entries.isEmpty()) {
			return true;
		}
		var normalized = email.toLowerCase(Locale.ROOT);
		return entries.stream().anyMatch(entry -> matches(normalized, entry.toLowerCase(Locale.ROOT)));
	}

	private static boolean matches(String email, String entry) {
		return entry.startsWith("@") ? email.endsWith(entry) : email.equals(entry);
	}

}
