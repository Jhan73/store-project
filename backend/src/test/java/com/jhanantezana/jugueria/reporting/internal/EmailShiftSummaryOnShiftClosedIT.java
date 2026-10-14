package com.jhanantezana.jugueria.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.notifications.ShiftSummaryEmail;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.ReportingTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class EmailShiftSummaryOnShiftClosedIT {

	static final Instant NOW = Instant.parse("2026-10-13T02:30:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@MockitoBean
	NotificationsApi notifications;

	@MockitoBean
	IdentityApi identity;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcClient jdbc;

	UUID cashier = UUID.randomUUID();

	UUID shift = UUID.randomUUID();

	@BeforeEach
	void twoAdminsAndAKnownCashier() {
		when(identity.activeAdminEmails()).thenReturn(List.of("owner@jugueria.pe", "manager@jugueria.pe"));
		when(identity.emailOf(cashier)).thenReturn(Optional.of("cajero@jugueria.pe"));
	}

	@AfterEach
	void cleanUp() {
		ReportingTables.clean(jdbc);
	}

	@Test
	void everyAdminGetsTheSummaryOfTheClosedShift() {
		publish(closed());

		var summary = ArgumentCaptor.forClass(ShiftSummaryEmail.class);
		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), summary.capture());
		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("manager@jugueria.pe"), any());
		assertThat(summary.getValue().cashier()).isEqualTo("cajero@jugueria.pe");
		assertThat(summary.getValue().closedAt()).isEqualTo(NOW);
		assertThat(summary.getValue().zone().getId()).isEqualTo("America/Lima");
		assertThat(summary.getValue().difference().amount()).isEqualByComparingTo("-5.00");
		assertThat(summary.getValue().note()).isEqualTo("Short on change");
		assertThat(summary.getValue().byMethod()).extracting(ShiftSummaryEmail.MethodTotal::method).containsExactly("CASH");
	}

	@Test
	void aRedeliveredCloseSendsNothingTwice() {
		var close = closed();

		publish(close);
		publish(close);

		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
		await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> verify(notifications, times(1)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any()));
	}

	@Test
	void theTransportIsNeverCalledInsideATransaction() {
		var inTransaction = new AtomicBoolean(true);
		doAnswer(call -> {
			inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
			return null;
		}).when(notifications).sendShiftSummaryEmail(any(), any());

		publish(closed());

		verify(notifications, timeout(20_000).atLeastOnce()).sendShiftSummaryEmail(any(), any());
		assertThat(inTransaction).isFalse();
	}

	@Test
	void aFailedSendReleasesTheClaimSoTheNextDeliveryTriesAgain() {
		doThrow(new IllegalStateException("transport down")).doNothing()
			.when(notifications)
			.sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
		var close = closed();

		publish(close);
		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> assertThat(jdbc.sql("select count(*) from reporting.shift_summary_email where shift_id = ?")
					.param(shift)
					.query(Long.class)
					.single()).isZero());
		publish(close);

		verify(notifications, timeout(20_000).times(2)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
	}

	@Test
	void withNoAdminToTellNothingIsSent() {
		when(identity.activeAdminEmails()).thenReturn(List.of());

		publish(closed());

		await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> verify(notifications, never()).sendShiftSummaryEmail(any(), any()));
	}

	ShiftClosed closed() {
		var zero = Money.zero(PEN);
		var totals = new ShiftTotals(zero, zero, zero, zero, zero,
				List.of(new ShiftTotals.MethodTotal(PaymentMethod.CASH, zero, zero, zero)));
		return new ShiftClosed(shift, cashier, false, Money.of("100.00", PEN), Money.of("95.00", PEN),
				Money.of("100.00", PEN), Money.of("-5.00", PEN), "Short on change", null, totals, cashier, Role.CASHIER,
				NOW);
	}

	void publish(Object event) {
		transactions.executeWithoutResult(status -> events.publishEvent(event));
	}

}
