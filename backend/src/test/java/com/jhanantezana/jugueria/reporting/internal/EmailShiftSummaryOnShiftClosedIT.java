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
import org.springframework.modulith.events.IncompleteEventPublications;
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

	@Autowired
	IncompleteEventPublications incompletePublications;

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
	void aFailedRecipientIsRetriedAloneWithoutRepublishingTheEvent() {
		doThrow(new IllegalStateException("transport down")).doNothing()
			.when(notifications)
			.sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());

		publish(closed());

		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("manager@jugueria.pe"), any());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(deliveries("owner@jugueria.pe")).isEmpty());
		// Only this shift's: other tests leave their own failed deliveries behind.
		incompletePublications.resubmitIncompletePublications(
				publication -> publication.getEvent() instanceof ShiftClosed closed && closed.shiftId().equals(shift));

		verify(notifications, timeout(20_000).times(2)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> verify(notifications, times(1)).sendShiftSummaryEmail(eq("manager@jugueria.pe"), any()));
	}

	@Test
	void aSentRecipientRecordsItsRealSendTime() {
		publish(closed());

		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			var rows = deliveries("owner@jugueria.pe");
			assertThat(rows).singleElement().satisfies(row -> {
				assertThat(row).containsEntry("status", "SENT");
				assertThat(row.get("sent_at")).isNotNull();
			});
		});
	}

	@Test
	void aStaleClaimFromACrashedDeliveryIsTakenOver() {
		claimed("owner@jugueria.pe", Instant.now().minus(Duration.ofHours(1)));

		publish(closed());

		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any());
	}

	@Test
	void aFreshClaimOfAnotherDeliveryIsRespectedAndTheEventStaysForRetry() {
		claimed("owner@jugueria.pe", Instant.now());

		publish(closed());

		verify(notifications, timeout(20_000)).sendShiftSummaryEmail(eq("manager@jugueria.pe"), any());
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> verify(notifications, never()).sendShiftSummaryEmail(eq("owner@jugueria.pe"), any()));
		assertThat(deliveries("owner@jugueria.pe")).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "CLAIMED"));
	}

	@Test
	void withNoAdminToTellNothingIsSent() {
		when(identity.activeAdminEmails()).thenReturn(List.of());

		publish(closed());

		await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(
				() -> verify(notifications, never()).sendShiftSummaryEmail(any(), any()));
	}

	List<java.util.Map<String, Object>> deliveries(String recipient) {
		return jdbc.sql("select * from reporting.shift_summary_delivery where shift_id = ? and recipient = ?")
			.param(shift)
			.param(recipient)
			.query()
			.listOfRows();
	}

	void claimed(String recipient, Instant claimedAt) {
		jdbc.sql("insert into reporting.shift_summary_delivery (shift_id, recipient, status, claimed_at) "
				+ "values (?, ?, 'CLAIMED', ?)")
			.params(shift, recipient, java.time.OffsetDateTime.ofInstant(claimedAt, java.time.ZoneOffset.UTC))
			.update();
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
