package com.jhanantezana.jugueria.notifications.internal;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

@Service
class NotificationsService implements NotificationsApi {

	private final EmailSender emailSender;

	private final StaffSetPasswordEmailTemplate template;

	private final AppEventsPublisher appEvents;

	private final Clock clock;

	NotificationsService(EmailSender emailSender, StaffSetPasswordEmailTemplate template,
			AppEventsPublisher appEvents, Clock clock) {
		this.emailSender = emailSender;
		this.template = template;
		this.appEvents = appEvents;
		this.clock = clock;
	}

	@Override
	public void sendStaffSetPasswordEmail(String toEmail, URI setPasswordLink, Instant linkExpiresAt) {
		var rendered = template.render(setPasswordLink, Instant.now(clock), linkExpiresAt);
		emailSender.send(new EmailMessage(toEmail, rendered.subject(), rendered.body()));
	}

	@Override
	public void publish(RealtimeTopic topic, String type, Map<String, String> ids) {
		appEvents.publish(topic, type, ids);
	}

}
