package com.jhanantezana.jugueria.catalog.internal;

import java.util.Arrays;
import java.util.Optional;

// Decided by the file's own first bytes: the client's Content-Type and file name are only claims.
enum ImageFormat {

	PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), WEBP("image/webp", "webp");

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };

	private static final byte[] WEBP_MARK = { 'W', 'E', 'B', 'P' };

	private final String contentType;

	private final String extension;

	ImageFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	String contentType() {
		return contentType;
	}

	String extension() {
		return extension;
	}

	static Optional<ImageFormat> detect(byte[] content) {
		if (startsWith(content, 0, PNG_SIGNATURE)) {
			return Optional.of(PNG);
		}
		if (startsWith(content, 0, JPEG_SIGNATURE)) {
			return Optional.of(JPEG);
		}
		if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARK)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
		return content.length >= offset + prefix.length
				&& Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
	}

}
