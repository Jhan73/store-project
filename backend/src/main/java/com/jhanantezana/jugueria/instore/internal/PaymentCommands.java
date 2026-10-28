package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.shared.Money;

/** Payment commands a retry could repeat; each registers its Idempotency-Key before doing anything else. */
@Service
public class PaymentCommands {

	private static final int CREATED = 201;

	private final TicketPayments payments;

	private final IdempotentRun idempotent;

	private final QuickSales quickSales;

	PaymentCommands(TicketPayments payments, QuickSales quickSales, IdempotentRun idempotent) {
		this.payments = payments;
		this.quickSales = quickSales;
		this.idempotent = idempotent;
	}

	@Transactional
	public Outcome<TicketView> quickSale(UUID actorId, UUID key, String requestHash, @Nullable String customerName,
			List<LineDraft> lines, PaymentMethod method, @Nullable Money received,
			@Nullable UUID walletProviderId) {
		return idempotent.once(actorId, key, requestHash, CREATED, TicketView.class,
				() -> quickSales.sell(customerName, lines, method, received, walletProviderId));
	}

	@Transactional
	public Outcome<TicketView> record(UUID actorId, UUID key, String requestHash, UUID ticketId, PaymentMethod method,
			Money amount, @Nullable Money received, @Nullable List<UUID> lineIds,
			@Nullable UUID walletProviderId) {
		return idempotent.once(actorId, key, requestHash, CREATED, TicketView.class,
				() -> payments.record(ticketId, method, amount, received, lineIds, walletProviderId));
	}

}
