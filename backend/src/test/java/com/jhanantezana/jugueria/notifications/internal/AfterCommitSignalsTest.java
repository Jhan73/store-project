package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;

class AfterCommitSignalsTest {

	private final AppEventsPublisher appEvents = mock(AppEventsPublisher.class);

	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

	private final AfterCommitSignals signals = new AfterCommitSignals(appEvents, transactionManager, Runnable::run);

	@BeforeEach
	void givenAnOpenTransactionSynchronization() {
		when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void clearSynchronization() {
		TransactionSynchronizationManager.clear();
	}

	private void commit() {
		for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCommit();
			synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		}
	}

	private void rollBack() {
		for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		}
	}

	@Test
	void emitsNothingUntilTheTransactionCommits() {
		signals.publish(RealtimeTopic.BOARD, "ORDER_READY", Map.of("orderId", "o-1"));

		verifyNoInteractions(appEvents);
	}

	@Test
	void emitsTheSignalOnceTheTransactionCommitted() {
		signals.publish(RealtimeTopic.BOARD, "ORDER_READY", Map.of("orderId", "o-1"));

		commit();

		verify(appEvents).publish(RealtimeTopic.BOARD, "ORDER_READY", Map.of("orderId", "o-1"));
	}

	@Test
	void emitsNothingWhenTheTransactionRollsBack() {
		signals.publish(RealtimeTopic.BOARD, "ORDER_READY", Map.of("orderId", "o-1"));

		rollBack();

		verifyNoInteractions(appEvents);
	}

	@Test
	void aFailingNotifyIsLoggedAndNeverReachesTheCommittedTransactionsCaller() {
		doThrow(new IllegalStateException("database down")).when(appEvents).publish(any(), any(), any());
		signals.publish(RealtimeTopic.DISPLAY, "DISPLAY_CHANGED", Map.of());

		commit();

		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
	}

	@Test
	void emitsRightAwayWhenThereIsNoTransactionToWaitFor() {
		TransactionSynchronizationManager.clear();

		signals.publish(RealtimeTopic.DISPLAY, "DISPLAY_CHANGED", Map.of());

		verify(appEvents).publish(RealtimeTopic.DISPLAY, "DISPLAY_CHANGED", Map.of());
	}

}
