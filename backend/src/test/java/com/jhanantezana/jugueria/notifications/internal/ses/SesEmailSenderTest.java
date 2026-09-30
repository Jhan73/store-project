package com.jhanantezana.jugueria.notifications.internal.ses;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jhanantezana.jugueria.notifications.internal.EmailMessage;
import com.jhanantezana.jugueria.notifications.internal.NotificationsProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

// SES has no local emulator; the SDK's endpoint override points the client at WireMock instead.
class SesEmailSenderTest {

	static final String SEND_EMAIL_PATH = "/v2/email/outbound-emails";

	static final NotificationsProperties.Ses SES = new NotificationsProperties.Ses("us-east-1", Duration.ofSeconds(10),
			Duration.ofSeconds(5));

	WireMockServer wireMock;

	SesV2Client client;

	@BeforeEach
	void setUp() {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();
		client = SesV2Client.builder()
			.region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
			.endpointOverride(URI.create(wireMock.baseUrl()))
			.build();
	}

	@AfterEach
	void tearDown() {
		client.close();
		wireMock.stop();
	}

	@Test
	void sendsTheMessageThroughSesWhenTheRecipientIsAllowed() {
		wireMock.stubFor(post(urlEqualTo(SEND_EMAIL_PATH))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"MessageId\":\"test-message-id\"}")));
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", List.of(), SES, "jugueria-backend");
		var sender = new SesEmailSender(client, properties);

		sender.send(new EmailMessage("new-staff@jugueria.pe", "Set your password", "body"));

		wireMock.verify(postRequestedFor(urlEqualTo(SEND_EMAIL_PATH))
			.withRequestBody(matchingJsonPath("$.FromEmailAddress", equalTo("no-reply@jugueria.jhanantezana.com")))
			.withRequestBody(matchingJsonPath("$.Destination.ToAddresses[0]", equalTo("new-staff@jugueria.pe")))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Subject.Data", equalTo("Set your password")))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Subject.Charset", equalTo("UTF-8")))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Body.Text.Data", equalTo("body")))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Body.Text.Charset", equalTo("UTF-8"))));
	}

	@Test
	void sendsUtf8EncodedSpanishContent() {
		wireMock.stubFor(post(urlEqualTo(SEND_EMAIL_PATH))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"MessageId\":\"test-message-id\"}")));
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", List.of(), SES, "jugueria-backend");
		var sender = new SesEmailSender(client, properties);
		var subject = "Configuración de tu contraseña";
		var body = "Hola, aquí tienes el enlace para el año que viene. ¡Éxitos!";

		sender.send(new EmailMessage("new-staff@jugueria.pe", subject, body));

		wireMock.verify(postRequestedFor(urlEqualTo(SEND_EMAIL_PATH))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Subject.Data", equalTo(subject)))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Subject.Charset", equalTo("UTF-8")))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Body.Text.Data", equalTo(body)))
			.withRequestBody(matchingJsonPath("$.Content.Simple.Body.Text.Charset", equalTo("UTF-8"))));
	}

	@Test
	void skipsSendingWhenTheRecipientIsOutsideTheAllowlist() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com",
				List.of("owner@jugueria.pe"), SES, "jugueria-backend");
		var sender = new SesEmailSender(client, properties);

		sender.send(new EmailMessage("someone-else@example.com", "Set your password", "body"));

		wireMock.verify(0, postRequestedFor(urlEqualTo(SEND_EMAIL_PATH)));
	}

}
