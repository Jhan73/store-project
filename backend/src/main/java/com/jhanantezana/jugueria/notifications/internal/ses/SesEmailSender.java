package com.jhanantezana.jugueria.notifications.internal.ses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.internal.EmailMessage;
import com.jhanantezana.jugueria.notifications.internal.EmailSender;
import com.jhanantezana.jugueria.notifications.internal.NotificationsProperties;
import com.jhanantezana.jugueria.notifications.internal.RecipientAllowlist;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@Profile({ "test", "prod" })
class SesEmailSender implements EmailSender {

	private static final String CHARSET = "UTF-8";

	private static final Logger log = LoggerFactory.getLogger(SesEmailSender.class);

	private final SesV2Client client;

	private final NotificationsProperties properties;

	private final RecipientAllowlist allowlist;

	SesEmailSender(SesV2Client client, NotificationsProperties properties) {
		this.client = client;
		this.properties = properties;
		this.allowlist = new RecipientAllowlist(properties.recipientAllowlist());
	}

	@Override
	public void send(EmailMessage message) {
		if (!allowlist.allows(message.to())) {
			log.warn("Skipped an email outside the test recipient allowlist");
			return;
		}
		client.sendEmail(SendEmailRequest.builder()
			.fromEmailAddress(properties.senderAddress())
			.destination(Destination.builder().toAddresses(message.to()).build())
			.content(EmailContent.builder()
				.simple(Message.builder()
					.subject(Content.builder().data(message.subject()).charset(CHARSET).build())
					.body(Body.builder().text(Content.builder().data(message.body()).charset(CHARSET).build()).build())
					.build())
				.build())
			.build());
	}

}
