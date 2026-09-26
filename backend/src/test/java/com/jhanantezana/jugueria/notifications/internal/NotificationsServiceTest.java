package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationsServiceTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	@Mock
	EmailSender emailSender;

	@Mock
	StaffSetPasswordEmailTemplate template;

	NotificationsService service;

	@BeforeEach
	void setUp() {
		service = new NotificationsService(emailSender, template, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void rendersAndSendsTheStaffSetPasswordEmail() {
		var link = URI.create("https://jugueria.jhanantezana.com/set-password?token=abc");
		var expiresAt = NOW.plus(Duration.ofHours(48));
		when(template.render(link, NOW, expiresAt)).thenReturn(new RenderedEmail("Set your password", "body"));

		service.sendStaffSetPasswordEmail("new-staff@jugueria.pe", link, expiresAt);

		var captor = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailSender).send(captor.capture());
		assertThat(captor.getValue().to()).isEqualTo("new-staff@jugueria.pe");
		assertThat(captor.getValue().subject()).isEqualTo("Set your password");
		assertThat(captor.getValue().body()).isEqualTo("body");
	}

	@Test
	void propagatesTheSendersFailure() {
		var link = URI.create("https://jugueria.jhanantezana.com/set-password?token=abc");
		when(template.render(any(), any(), any())).thenReturn(new RenderedEmail("subject", "body"));
		var failure = new IllegalStateException("no transport");
		org.mockito.Mockito.doThrow(failure).when(emailSender).send(any());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendStaffSetPasswordEmail("x@jugueria.pe", link, NOW))
			.isSameAs(failure);
	}

}
