package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class UnavailableEmailSenderTest {

	@Test
	void failsLoudlyInsteadOfPretendingToDeliver() {
		var sender = new UnavailableEmailSender();

		assertThatIllegalStateException()
			.isThrownBy(() -> sender.send(new EmailMessage("to@jugueria.pe", "subject", "body")));
	}

}
