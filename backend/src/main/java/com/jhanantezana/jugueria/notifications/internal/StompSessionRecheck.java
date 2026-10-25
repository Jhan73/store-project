package com.jhanantezana.jugueria.notifications.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.AccountActivity;

// A STOMP session is authenticated once, at CONNECT, and never again. The deactivation signal closes it at once, but a
// signal can be lost (a LISTEN connection that was down), so the account is also looked up when the token the session
// connected with expires and every few minutes after that. Runs on every task, each over the sessions it holds.
@Component
class StompSessionRecheck implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(StompSessionRecheck.class);

	private final StompSessionRegistry sessions;

	private final AccountActivity accounts;

	private final StompSessionRecheckProperties properties;

	private final Clock clock;

	StompSessionRecheck(StompSessionRegistry sessions, AccountActivity accounts,
			StompSessionRecheckProperties properties, Clock clock) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addFixedDelayTask(new IntervalTask(this::run, properties.sweepInterval(), properties.sweepInterval()));
	}

	void run() {
		var now = Instant.now(clock);
		for (var userId : sessions.usersDue(now)) {
			try {
				recheck(userId, now);
			}
			catch (RuntimeException e) {
				// The session stays and the user is due again on the next run.
				log.warn("Could not recheck an account with an open realtime session", e);
			}
		}
	}

	private void recheck(String userId, Instant now) {
		UUID account;
		try {
			account = UUID.fromString(userId);
		}
		catch (IllegalArgumentException e) {
			sessions.rechecked(userId, now.plus(properties.accountRecheckInterval()));
			return;
		}
		if (accounts.isActive(account)) {
			sessions.rechecked(userId, now.plus(properties.accountRecheckInterval()));
		}
		else {
			sessions.closeSessionsOf(userId);
		}
	}

}
