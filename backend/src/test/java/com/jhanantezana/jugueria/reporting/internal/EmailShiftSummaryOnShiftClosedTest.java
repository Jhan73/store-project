package com.jhanantezana.jugueria.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.StoreApi;

class EmailShiftSummaryOnShiftClosedTest {

	static final Instant NOW = Instant.parse("2026-10-13T02:30:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	final IdentityApi identity = mock(IdentityApi.class);

	final NotificationsApi notifications = mock(NotificationsApi.class);

	final StoreApi store = mock(StoreApi.class);

	final ShiftSummaryEmailClaims claims = mock(ShiftSummaryEmailClaims.class);

	final ReportingProperties properties = new ReportingProperties(Duration.ofMinutes(5));

	EmailShiftSummaryOnShiftClosed listener;

	@BeforeEach
	void setUp() {
		when(identity.activeAdminEmails()).thenReturn(List.of("owner@jugueria.pe"));
		when(identity.emailOf(any())).thenReturn(Optional.empty());
		when(store.timeZone()).thenReturn(ZoneId.of("America/Lima"));
		listener = new EmailShiftSummaryOnShiftClosed(identity, notifications, store, claims,
				Clock.fixed(NOW, ZoneOffset.UTC), properties);
	}

	@Test
	void aFailingReleaseNeverMasksTheSendFailure() {
		when(claims.claim(any(), anyString(), any(), any())).thenReturn(ShiftSummaryEmailClaims.Claim.CLAIMED);
		var sendFailure = new IllegalStateException("transport down");
		doThrow(sendFailure).when(notifications).sendShiftSummaryEmail(anyString(), any());
		var releaseFailure = new IllegalStateException("database down");
		doThrow(releaseFailure).when(claims).release(any(), anyString());

		assertThatThrownBy(() -> listener.on(closed())).isSameAs(sendFailure)
			.satisfies(e -> assertThat(e.getSuppressed()).containsExactly(releaseFailure));
	}

	@Test
	void aRecipientAlreadySentIsSkippedAndNeverMarkedAgain() {
		when(claims.claim(any(), anyString(), any(), any())).thenReturn(ShiftSummaryEmailClaims.Claim.ALREADY_SENT);

		listener.on(closed());

		verify(notifications, never()).sendShiftSummaryEmail(anyString(), any());
		verify(claims, never()).markSent(any(), anyString(), any());
	}

	@Test
	void aClaimHeldByAnotherDeliveryFailsTheListenerSoTheEventIsRetried() {
		when(claims.claim(any(), anyString(), any(), any())).thenReturn(ShiftSummaryEmailClaims.Claim.BUSY);

		assertThatThrownBy(() -> listener.on(closed())).isInstanceOf(IllegalStateException.class);
		verify(notifications, never()).sendShiftSummaryEmail(anyString(), any());
	}

	ShiftClosed closed() {
		var zero = Money.zero(PEN);
		var totals = new ShiftTotals(zero, zero, zero, zero, zero,
				List.of(new ShiftTotals.MethodTotal(PaymentMethod.CASH, zero, zero, zero)));
		return new ShiftClosed(UUID.randomUUID(), UUID.randomUUID(), false, Money.of("100.00", PEN),
				Money.of("100.00", PEN), Money.of("100.00", PEN), Money.zero(PEN), null, null, totals, null,
				Role.CASHIER, NOW);
	}

}
