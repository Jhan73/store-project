package com.jhanantezana.jugueria.notifications.internal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.postgresql.PGConnection;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

// A raw LISTEN connection standing in for a task's bridge, so a test sees exactly what NOTIFY carried.
final class AppEventsProbe implements AutoCloseable {

	private final Connection connection;

	// One getNotifications() call can return several pending notifications at once; accumulate across polls.
	private final List<String> received = new ArrayList<>();

	AppEventsProbe(JdbcConnectionDetails details) throws SQLException {
		connection = DriverManager.getConnection(details.getJdbcUrl(), details.getUsername(), details.getPassword());
		try (var statement = connection.createStatement()) {
			statement.execute("LISTEN app_events");
		}
	}

	List<String> matching(String marker) {
		return received.stream().filter(n -> n.contains(marker)).toList();
	}

	long countMatching(String marker) {
		try {
			var notifications = connection.unwrap(PGConnection.class).getNotifications(200);
			if (notifications != null) {
				for (var notification : notifications) {
					received.add(notification.getParameter());
				}
			}
			return matching(marker).size();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to poll for a notification", e);
		}
	}

	@Override
	public void close() throws SQLException {
		connection.close();
	}

}
