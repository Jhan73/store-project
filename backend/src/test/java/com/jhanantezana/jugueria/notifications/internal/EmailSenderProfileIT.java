package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

import software.amazon.awssdk.services.sesv2.SesV2Client;

// No profile active here (Surefire/Failsafe default) must never resolve the SES adapter.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class EmailSenderProfileIT {

	@Autowired
	ApplicationContext context;

	@Autowired
	EmailSender emailSender;

	@Test
	void resolvesTheSafeAdapterWithNoProfileActive() {
		assertThat(emailSender).isInstanceOf(UnavailableEmailSender.class);
	}

	@Test
	void neverCreatesASesClientWithNoProfileActive() {
		assertThatThrownBy(() -> context.getBean(SesV2Client.class)).isInstanceOf(NoSuchBeanDefinitionException.class);
	}

}
