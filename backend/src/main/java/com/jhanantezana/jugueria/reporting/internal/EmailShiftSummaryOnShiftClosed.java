package com.jhanantezana.jugueria.reporting.internal;

import java.time.Clock;
import java.time.Instant;

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

	EmailShiftSummaryOnShiftClosed(IdentityApi identity, NotificationsApi notifications, StoreApi store,
			ShiftSummaryEmailClaims claims, Clock clock) {
		this.identity = identity;
		this.notifications = notifications;
		this.store = store;
		this.claims = claims;
		this.clock = clock;
	}

	// No transaction: the mail goes through an external system, so nothing may hold a connection meanwhile.
	// The claim makes a second delivery a no-op; a failed send gives it back so the next delivery tries again.
	@ApplicationModuleListener
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void on(ShiftClosed event) {
		var admins = identity.activeAdminEmails();
		if (admins.isEmpty() || !claims.claim(event.shiftId(), Instant.now(clock))) {
			return;
		}
		try {
			var summary = summary(event);
			for (var admin : admins) {
				notifications.sendShiftSummaryEmail(admin, summary);
			}
		}
		catch (RuntimeException e) {
			claims.release(event.shiftId());
			throw e;
		}
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
