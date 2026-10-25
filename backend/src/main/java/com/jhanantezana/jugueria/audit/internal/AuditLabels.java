package com.jhanantezana.jugueria.audit.internal;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.identity.IdentityApi;
import com.jhanantezana.jugueria.instore.InstoreApi;

/**
 * What a person calls an audited entity, for events that carry only its id. Read in the publisher's own transaction,
 * so the entity is still there; a label is never a secret or personal contact data: a name, a ticket number. A
 * person is labeled by username, never by email.
 */
@Component
class AuditLabels {

	private final IdentityApi identity;

	private final CatalogApi catalog;

	private final InstoreApi instore;

	AuditLabels(IdentityApi identity, CatalogApi catalog, InstoreApi instore) {
		this.identity = identity;
		this.catalog = catalog;
		this.instore = instore;
	}

	@Nullable String user(UUID userId) {
		return identity.usernameOf(userId).orElse(null);
	}

	@Nullable String product(UUID productId) {
		return catalog.productName(productId).orElse(null);
	}

	@Nullable String ticket(UUID ticketId) {
		return instore.ticketNumber(ticketId).map(AuditLabels::ticketNumber).orElse(null);
	}

	static String ticketNumber(long number) {
		return "#" + number;
	}

}
