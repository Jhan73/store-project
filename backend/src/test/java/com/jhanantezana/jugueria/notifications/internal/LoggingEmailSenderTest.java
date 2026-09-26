package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class LoggingEmailSenderTest {

	@Test
	void logsWithoutThrowing() {
		var sender = new LoggingEmailSender();

		assertThatCode(() -> sender.send(new EmailMessage("to@jugueria.pe", "subject", "body"))).doesNotThrowAnyException();
	}

}
