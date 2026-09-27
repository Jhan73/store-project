package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Verifies PostgreSQL's own commit-gated NOTIFY delivery, without a running STOMP broker.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class AppEventsPublisherIT {

	@Autowired
	NotificationsApi notifications;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcConnectionDetails connectionDetails;

	Connection probe;

	@AfterEach
	void closeProbe() throws SQLException {
		if (probe != null) {
			probe.close();
		}
	}

	@Test
	void deliversTheNotificationOnlyAfterTheTransactionCommits() throws SQLException {
		listen();
		var template = new TransactionTemplate(transactionManager);

		template.executeWithoutResult(status -> notifications.publish(RealtimeTopic.STORE_STATUS,
				"STORE_SETTINGS_CHANGED", Map.of("settingsId", "committed-1")));

		var notification = awaitNotification();
		assertThat(notification)
			.isEqualTo("{\"topic\":\"store-status\",\"type\":\"STORE_SETTINGS_CHANGED\",\"ids\":{\"settingsId\":\"committed-1\"}}");
	}

	@Test
	void neverDeliversTheNotificationWhenTheTransactionRollsBack() throws SQLException {
		listen();
		var template = new TransactionTemplate(transactionManager);

		template.execute(status -> {
			notifications.publish(RealtimeTopic.CATALOG, "PRODUCT_AVAILABILITY_CHANGED",
					Map.of("productId", "rolled-back-1"));
			status.setRollbackOnly();
			return null;
		});

		assertThat(awaitNotificationOrNull()).isNull();
	}

	private void listen() throws SQLException {
		probe = DriverManager.getConnection(connectionDetails.getJdbcUrl(), connectionDetails.getUsername(),
				connectionDetails.getPassword());
		try (Statement statement = probe.createStatement()) {
			statement.execute("LISTEN app_events");
		}
	}

	private String awaitNotification() throws SQLException {
		var notification = awaitNotificationOrNull();
		assertThat(notification).isNotNull();
		return notification;
	}

	private String awaitNotificationOrNull() throws SQLException {
		var notifications = probe.unwrap(PGConnection.class).getNotifications(5_000);
		return notifications == null || notifications.length == 0 ? null : notifications[0].getParameter();
	}

}
