package com.jhanantezana.jugueria.shared;

public final class ETags {

	private ETags() {
	}

	public static String format(long version) {
		return "\"" + version + "\"";
	}

	// Accepts a bare number too, since some HTTP clients strip quotes from a copy-pasted ETag.
	public static long parse(String header) {
		var trimmed = header.strip();
		var unquoted = trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
				? trimmed.substring(1, trimmed.length() - 1) : trimmed;
		try {
			return Long.parseLong(unquoted);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Malformed ETag: " + header, e);
		}
	}

}
