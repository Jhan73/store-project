package com.jhanantezana.jugueria.reporting.internal;

import java.util.List;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.SoldLine;
import com.jhanantezana.jugueria.instore.TicketLineComped;
import com.jhanantezana.jugueria.shared.Money;

@Component
class RecordSalesLineOnTicketLineComped {

	private final SalesLineRecorder recorder;

	RecordSalesLineOnTicketLineComped(SalesLineRecorder recorder) {
		this.recorder = recorder;
	}

	@ApplicationModuleListener
	void on(TicketLineComped event) {
		var line = new SoldLine(event.lineId(), event.productId(), event.productName(), event.quantity(),
				event.unitPrice(), Money.zero(event.amount().currency()), event.modifiers());
		recorder.record(event.ticketId(), event.ticketId(), event.ticketType(), event.quickSale(), List.of(line), false,
				true, event.actorId(), event.occurredAt());
	}

}
