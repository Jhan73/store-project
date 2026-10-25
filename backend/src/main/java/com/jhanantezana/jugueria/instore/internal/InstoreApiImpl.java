package com.jhanantezana.jugueria.instore.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.instore.InstoreApi;

@Service
class InstoreApiImpl implements InstoreApi {

	private final Tickets tickets;

	InstoreApiImpl(Tickets tickets) {
		this.tickets = tickets;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Long> ticketNumber(UUID ticketId) {
		return tickets.find(ticketId).map(Tickets.TicketRow::number);
	}

}
