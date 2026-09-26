package com.jhanantezana.jugueria.notifications.internal.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.jhanantezana.jugueria.notifications.internal.EmailMessage;
import com.jhanantezana.jugueria.notifications.internal.NotificationsProperties;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

	@Mock
	JavaMailSender mailSender;

	SmtpEmailSender sender;

	@BeforeEach
	void setUp() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", List.of(),
				new NotificationsProperties.Ses("us-east-1", Duration.ofSeconds(10), Duration.ofSeconds(5)));
		sender = new SmtpEmailSender(mailSender, properties);
	}

	@Test
	void sendsAPlainTextMessageThroughTheConfiguredSender() {
		sender.send(new EmailMessage("new-staff@jugueria.pe", "Set your password", "body"));

		var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getFrom()).isEqualTo("no-reply@jugueria.jhanantezana.com");
		assertThat(captor.getValue().getTo()).containsExactly("new-staff@jugueria.pe");
		assertThat(captor.getValue().getSubject()).isEqualTo("Set your password");
		assertThat(captor.getValue().getText()).isEqualTo("body");
	}

}
