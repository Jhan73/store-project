package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.catalog.QuickSaleItem;
import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Money;

/**
 * A counter sale paid upfront: the ticket, its lines, the board card, the payment and the stock all commit together
 * or not at all, so a sale the shelf or the register cannot take leaves nothing behind.
 */
@Service
public class QuickSales {

	private final TicketService ticketService;

	private final TicketLines lines;

	private final TicketSending sending;

	private final TicketPayments payments;

	private final Tickets tickets;

	private final Shifts shifts;

	private final CatalogApi catalog;

	private final CurrentActor currentActor;

	QuickSales(TicketService ticketService, TicketLines lines, TicketSending sending, TicketPayments payments,
			Tickets tickets, Shifts shifts, CatalogApi catalog, CurrentActor currentActor) {
		this.ticketService = ticketService;
		this.lines = lines;
		this.sending = sending;
		this.payments = payments;
		this.tickets = tickets;
		this.shifts = shifts;
		this.catalog = catalog;
		this.currentActor = currentActor;
	}

	/** Sells the lines and takes the whole total with one payment; cash states the amount received. */
	@Transactional
	public TicketView sell(@Nullable String customerName, List<LineDraft> drafts, PaymentMethod method,
			@Nullable Money received, @Nullable UUID walletProviderId) {
		var actor = currentActor.id();
		if (actor == null) {
			throw new IllegalStateException("A quick sale needs a signed-in user");
		}
		shifts.requireWritableOf(actor);
		drafts.forEach(this::requirePinned);
		var id = ticketService.openQuickSale(customerName).id();
		for (var draft : drafts) {
			lines.add(id, draft);
		}
		sending.send(id);
		var ticket = tickets.require(id);
		var total = TicketRules.total(tickets.lines(id), ticket.currency());
		return payments.record(id, method, total, received, null, walletProviderId);
	}

	// An unknown product is left to the line itself, which answers not found.
	private void requirePinned(LineDraft draft) {
		if (catalog.productExists(draft.productId()) && !catalog.isQuickSalePinned(draft.productId())) {
			throw new BusinessException(InstoreError.PRODUCT_NOT_QUICK_SALE,
					"Product " + draft.productId() + " is not pinned for quick sales");
		}
	}

	/** The products the admin pinned as frequent items that can be sold right now. */
	@Transactional(readOnly = true)
	public List<QuickSaleItem> frequentItems() {
		return catalog.quickSaleItems();
	}

}
