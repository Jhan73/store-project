package com.jhanantezana.jugueria.notifications.internal;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.jhanantezana.jugueria.notifications.NotificationsApi;

@Service
class NotificationsService implements NotificationsApi {

	private final EmailSender emailSender;

	private final StaffSetPasswordEmailTemplate template;

	private final Clock clock;

	NotificationsService(EmailSender emailSender, StaffSetPasswordEmailTemplate template, Clock clock) {
		this.emailSender = emailSender;
		this.template = template;
		this.clock = clock;
	}

	@Override
	public void sendStaffSetPasswordEmail(String toEmail, URI setPasswordLink, Instant linkExpiresAt) {
		var rendered = template.render(setPasswordLink, Instant.now(clock), linkExpiresAt);
		emailSender.send(new EmailMessage(toEmail, rendered.subject(), rendered.body()));
	}

}
