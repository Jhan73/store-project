package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// For signals people wait on (the kitchen board, the customer screen): the NOTIFY goes out as soon as the producer's
// own transaction committed, without waiting for the event publication registry. A rollback emits nothing.
// The committing thread never runs the NOTIFY itself: it still holds its pooled connection, and needing a second one
// there could stall the pool under load.
@Component
class AfterCommitSignals {

	private static final Logger log = LoggerFactory.getLogger(AfterCommitSignals.class);

	private final AppEventsPublisher appEvents;

	private final TransactionTemplate transactions;

	private final Executor executor;

	@Autowired
	AfterCommitSignals(AppEventsPublisher appEvents, PlatformTransactionManager transactionManager) {
		this(appEvents, transactionManager, task -> Thread.ofVirtual().name("signal-after-commit").start(task));
	}

	AfterCommitSignals(AppEventsPublisher appEvents, PlatformTransactionManager transactionManager, Executor executor) {
		this.appEvents = appEvents;
		this.transactions = new TransactionTemplate(transactionManager);
		this.executor = executor;
	}

	void publish(RealtimeTopic topic, String type, Map<String, String> ids) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			emit(topic, type, ids);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				executor.execute(() -> emit(topic, type, ids));
			}

		});
	}

	// A failure is only logged: clients also re-read on their own schedule, and the business change is committed.
	private void emit(RealtimeTopic topic, String type, Map<String, String> ids) {
		try {
			transactions.executeWithoutResult(status -> appEvents.publish(topic, type, ids));
		}
		catch (RuntimeException e) {
			log.error("Could not signal {} {} after commit", topic.channel(), type, e);
		}
	}

}
