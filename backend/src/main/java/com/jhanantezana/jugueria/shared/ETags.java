package com.jhanantezana.jugueria.shared;

public final class ETags {

	private ETags() {
	}

	public static String format(long version) {
		return "\"" + version + "\"";
	}

	// Accepts a bare number and a weak W/"n" prefix too, since clients normalize copy-pasted ETags differently.
	public static long parse(String header) {
		var trimmed = header.strip();
		var unprefixed = trimmed.startsWith("W/") ? trimmed.substring(2).strip() : trimmed;
		var unquoted = unprefixed.length() >= 2 && unprefixed.startsWith("\"") && unprefixed.endsWith("\"")
				? unprefixed.substring(1, unprefixed.length() - 1) : unprefixed;
		try {
			return Long.parseLong(unquoted);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Malformed ETag: " + header, e);
		}
	}

}
