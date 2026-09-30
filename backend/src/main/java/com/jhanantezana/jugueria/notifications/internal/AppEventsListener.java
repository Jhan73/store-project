package com.jhanantezana.jugueria.notifications.internal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import com.jhanantezana.jugueria.shared.RealtimeSignalReceived;

import tools.jackson.databind.json.JsonMapper;

// One dedicated, non-pooled connection per task: a slow or blocked listener thread must never starve the
// Hikari pool the rest of the app needs.
@Component
@ConditionalOnWebApplication(type = Type.SERVLET)
class AppEventsListener implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(AppEventsListener.class);

	private static final String CHANNEL = "app_events";

	// Long: reconnection after a dropped connection happens immediately (closing it unblocks the read),
	// not after this timeout, so a longer value only reduces idle wake-ups.
	private static final int POLL_TIMEOUT_MILLIS = 30_000;

	private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

	private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

	private final JdbcConnectionDetails connectionDetails;

	private final SimpMessagingTemplate messagingTemplate;

	private final JsonMapper mapper;

	private final String applicationName;

	private final ApplicationEventPublisher localEvents;

	private volatile boolean running;

	private volatile Connection connection;

	private Thread worker;

	AppEventsListener(JdbcConnectionDetails connectionDetails, SimpMessagingTemplate messagingTemplate,
			JsonMapper mapper, NotificationsProperties properties, ApplicationEventPublisher localEvents) {
		this.localEvents = localEvents;
		this.connectionDetails = connectionDetails;
		this.messagingTemplate = messagingTemplate;
		this.mapper = mapper;
		this.applicationName = properties.listenerApplicationName();
	}

	@Override
	public void start() {
		running = true;
		worker = Thread.ofVirtual().name("app-events-listener").start(this::run);
	}

	@Override
	public void stop() {
		running = false;
		// Closing the connection unblocks a pending getNotifications() read immediately.
		closeQuietly();
		if (worker != null) {
			try {
				worker.join(Duration.ofSeconds(5));
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void run() {
		var backoff = INITIAL_BACKOFF;
		while (running) {
			try {
				connect();
				backoff = INITIAL_BACKOFF;
				poll();
			}
			catch (SQLException e) {
				if (!running) {
					break;
				}
				log.warn("app_events LISTEN connection lost, reconnecting in {}", backoff, e);
				sleep(backoff);
				backoff = nextBackoff(backoff);
			}
		}
		closeQuietly();
	}

	private static Duration nextBackoff(Duration current) {
		var doubled = current.multipliedBy(2);
		return doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
	}

	private void connect() throws SQLException {
		var props = new Properties();
		props.setProperty("user", connectionDetails.getUsername());
		props.setProperty("password", connectionDetails.getPassword());
		// Lets tests identify this instance's own backend in pg_stat_activity, unambiguously and across reconnects.
		props.setProperty("ApplicationName", applicationName);
		var conn = DriverManager.getConnection(connectionDetails.getJdbcUrl(), props);
		try (var statement = conn.createStatement()) {
			statement.execute("LISTEN " + CHANNEL);
		}
		this.connection = conn;
	}

	private void poll() throws SQLException {
		var pg = connection.unwrap(PGConnection.class);
		while (running) {
			var notifications = pg.getNotifications(POLL_TIMEOUT_MILLIS);
			if (notifications != null) {
				for (var notification : notifications) {
					forward(notification.getParameter());
				}
			}
		}
	}

	void forward(String json) {
		try {
			var signal = mapper.readValue(json, RealtimeSignal.class);
			// Before the STOMP send: a client that re-fetches on the message must not meet the stale cache.
			publishLocally(signal);
			var payload = mapper.writeValueAsString(signal.outbound());
			// text/plain, not application/json: the body is JSON text, but StringMessageConverter (the
			// simplest client-side converter for a signal client code just re-parses) only accepts text/plain.
			var headers = SimpMessageHeaderAccessor.create();
			headers.setContentType(MimeTypeUtils.TEXT_PLAIN);
			headers.setLeaveMutable(true);
			messagingTemplate.convertAndSend("/topic/" + signal.topic(), payload, headers.getMessageHeaders());
		}
		catch (Exception e) {
			// A malformed payload must never kill the listener loop; local subscribers just miss this signal.
			log.error("Failed to forward an app_events notification", e);
		}
	}

	private void publishLocally(RealtimeSignal signal) {
		try {
			localEvents.publishEvent(new RealtimeSignalReceived(signal.topic(), signal.type(), signal.ids()));
		}
		catch (RuntimeException e) {
			// A failing reaction in another module must not stop this task's own subscribers hearing the signal.
			log.error("A local reaction to an app_events notification failed", e);
		}
	}

	private void closeQuietly() {
		var conn = connection;
		if (conn != null) {
			try {
				conn.close();
			}
			catch (SQLException ignored) {
				// Closing an already-broken connection; nothing to act on.
			}
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
