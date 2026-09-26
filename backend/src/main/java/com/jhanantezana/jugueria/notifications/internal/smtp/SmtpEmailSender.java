package com.jhanantezana.jugueria.notifications.internal.smtp;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.internal.EmailMessage;
import com.jhanantezana.jugueria.notifications.internal.EmailSender;
import com.jhanantezana.jugueria.notifications.internal.NotificationsProperties;

// Mailpit locally: the link is visible in its web UI, never in application logs.
@Component
@Profile("local")
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;

	private final NotificationsProperties properties;

	SmtpEmailSender(JavaMailSender mailSender, NotificationsProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void send(EmailMessage message) {
		var mail = new SimpleMailMessage();
		mail.setFrom(properties.senderAddress());
		mail.setTo(message.to());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		mailSender.send(mail);
	}

}
