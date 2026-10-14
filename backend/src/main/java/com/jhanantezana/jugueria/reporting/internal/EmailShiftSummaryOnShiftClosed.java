package com.jhanantezana.jugueria.reporting.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.notifications.ShiftSummaryEmail;
import com.jhanantezana.jugueria.store.StoreApi;

@Component
class EmailShiftSummaryOnShiftClosed {

	private final IdentityApi identity;

	private final NotificationsApi notifications;

	private final StoreApi store;

	private final ShiftSummaryEmailClaims claims;

	private final Clock clock;

	private final ReportingProperties properties;

	EmailShiftSummaryOnShiftClosed(IdentityApi identity, NotificationsApi notifications, StoreApi store,
			ShiftSummaryEmailClaims claims, Clock clock, ReportingProperties properties) {
		this.identity = identity;
		this.notifications = notifications;
		this.store = store;
		this.claims = claims;
		this.clock = clock;
		this.properties = properties;
	}

	// No transaction: the mail goes through an external system, so nothing may hold a connection meanwhile.
	// Every admin is tried, and a failure makes the delivery fail so the resubmission job retries the rest only.
	@ApplicationModuleListener
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void on(ShiftClosed event) {
		var admins = identity.activeAdminEmails();
		if (admins.isEmpty()) {
			return;
		}
		var summary = summary(event);
		RuntimeException failure = null;
		for (var admin : admins) {
			try {
				sendOnce(event.shiftId(), admin, summary);
			}
			catch (RuntimeException e) {
				if (failure == null) {
					failure = e;
				}
				else {
					failure.addSuppressed(e);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	private void sendOnce(UUID shiftId, String admin, ShiftSummaryEmail summary) {
		var claim = claims.claim(shiftId, admin, Instant.now(clock), properties.shiftSummaryEmailLease());
		if (claim == ShiftSummaryEmailClaims.Claim.ALREADY_SENT) {
			return;
		}
		if (claim == ShiftSummaryEmailClaims.Claim.BUSY) {
			throw new IllegalStateException("The shift summary for " + shiftId + " is being sent by another delivery");
		}
		try {
			notifications.sendShiftSummaryEmail(admin, summary);
		}
		catch (RuntimeException e) {
			try {
				claims.release(shiftId, admin);
			}
			catch (RuntimeException releaseFailure) {
				e.addSuppressed(releaseFailure);
			}
			throw e;
		}
		claims.markSent(shiftId, admin, Instant.now(clock));
	}

	private ShiftSummaryEmail summary(ShiftClosed event) {
		var totals = event.totals();
		var cashier = identity.emailOf(event.cashierId()).orElse(event.cashierId().toString());
		var byMethod = totals.byMethod()
			.stream()
			.map(total -> new ShiftSummaryEmail.MethodTotal(total.method().name(), total.recorded(), total.voided(),
					total.net()))
			.toList();
		return new ShiftSummaryEmail(cashier, event.occurredAt(), store.timeZone(), event.forced(),
				event.openingFloat(), event.countedCash(), event.expectedCash(), event.difference(),
				event.forced() ? event.forceReason() : event.differenceNote(), totals.cashReceived(),
				totals.changeGiven(), totals.voidedCash(), totals.cashIn(), totals.cashOut(), byMethod);
	}

}
