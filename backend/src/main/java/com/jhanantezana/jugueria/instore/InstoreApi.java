package com.jhanantezana.jugueria.instore;

import java.util.Optional;
import java.util.UUID;

public interface InstoreApi {

	/** The number printed on the ticket, empty for an unknown ticket. */
	Optional<Long> ticketNumber(UUID ticketId);

}
