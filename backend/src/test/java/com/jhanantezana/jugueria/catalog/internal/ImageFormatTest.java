package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageFormatTest {

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };

	static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };

	@Test
	void recognisesAPng() {
		var format = ImageFormat.detect(PNG).orElseThrow();

		assertThat(format).isEqualTo(ImageFormat.PNG);
		assertThat(format.contentType()).isEqualTo("image/png");
		assertThat(format.extension()).isEqualTo("png");
	}

	@Test
	void recognisesAJpeg() {
		var format = ImageFormat.detect(JPEG).orElseThrow();

		assertThat(format).isEqualTo(ImageFormat.JPEG);
		assertThat(format.contentType()).isEqualTo("image/jpeg");
		assertThat(format.extension()).isEqualTo("jpg");
	}

	@Test
	void recognisesAWebp() {
		var format = ImageFormat.detect(WEBP).orElseThrow();

		assertThat(format).isEqualTo(ImageFormat.WEBP);
		assertThat(format.contentType()).isEqualTo("image/webp");
		assertThat(format.extension()).isEqualTo("webp");
	}

	@Test
	void rejectsTextThatOnlyClaimsToBeAnImage() {
		assertThat(ImageFormat.detect("GIF89a not really".getBytes(StandardCharsets.UTF_8))).isEmpty();
		assertThat(ImageFormat.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
			.isEmpty();
	}

	@Test
	void rejectsARiffFileThatIsNotWebp() {
		var wave = new byte[] { 'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ' };

		assertThat(ImageFormat.detect(wave)).isEmpty();
	}

	@Test
	void rejectsEmptyAndTruncatedContent() {
		assertThat(ImageFormat.detect(new byte[0])).isEmpty();
		assertThat(ImageFormat.detect(new byte[] { (byte) 0xFF, (byte) 0xD8 })).isEmpty();
		assertThat(ImageFormat.detect(new byte[] { 'R', 'I', 'F', 'F' })).isEmpty();
	}

}
