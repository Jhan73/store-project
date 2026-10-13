package com.jhanantezana.jugueria.instore.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.instore.TicketOpened;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.StoreApi;

/** Opening and reading tickets; the commands on their lines live in their own services. */
@Service
public class TicketService {

	private final Tickets tickets;

	private final Payments payments;

	private final StoreApi store;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	TicketService(Tickets tickets, Payments payments, StoreApi store, ApplicationEventPublisher events, CurrentActor currentActor,
			Clock clock) {
		this.tickets = tickets;
		this.payments = payments;
		this.store = store;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional
	public TicketView open(@Nullable UUID tableId, @Nullable String counterName, @Nullable String note) {
		var customer = counterName == null || counterName.isBlank() ? null : counterName.strip();
		if ((tableId == null) == (customer == null)) {
			throw new BusinessException(InstoreError.INVALID_TICKET,
					"A ticket is for a table or for the counter's customer, not for both or neither");
		}
		var now = Instant.now(clock);
		var id = Ids.newId();
		var type = tableId == null ? TicketType.COUNTER : TicketType.TABLE;
		if (tableId != null) {
			tickets.requireActiveTable(tableId);
		}
		var inserted = tickets.insert(id, type, tableId, customer, blankToNull(note), store.currency(), requireActor(), now);
		if (!inserted) {
			throw new BusinessException(InstoreError.TABLE_OCCUPIED, "Table " + tableId + " already has an open ticket");
		}
		var ticket = tickets.require(id);
		events.publishEvent(new TicketOpened(id, ticket.number(), type, tableId, customer, currentActor.id(),
				currentActor.role(), now));
		if (tableId != null) {
			events.publishEvent(new TableOccupancyChanged(tableId, now));
		}
		return view(ticket);
	}

	@Transactional(readOnly = true)
	public TicketView get(UUID id) {
		return view(tickets.require(id));
	}

	@Transactional(readOnly = true)
	public List<TicketSummary> listOpen() {
		return tickets.open();
	}

	TicketView view(Tickets.TicketRow ticket) {
		var lines = tickets.lines(ticket.id());
		var total = TicketRules.total(lines, ticket.currency());
		var recorded = payments.ofTicket(ticket.id());
		var paid = recorded.stream().filter(payment -> !payment.voided()).map(PaymentView::paid).reduce(Money.zero(ticket.currency()), Money::plus);
		var balance = ticket.status() == TicketStatus.OPEN ? total.minus(paid) : Money.zero(ticket.currency());
		return new TicketView(ticket.id(), ticket.number(), ticket.type(), ticket.tableId(), ticket.tableName(),
				ticket.counterName(), ticket.note(), ticket.status(), ticket.billRequested(), total, ticket.createdAt(),
				lines, ticket.quickSale(), paid, balance, recorded, payments.saleVoidOf(ticket.id()).orElse(null));
	}

	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A ticket command needs a signed-in user");
		}
		return id;
	}

	static @Nullable String blankToNull(@Nullable String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

}
