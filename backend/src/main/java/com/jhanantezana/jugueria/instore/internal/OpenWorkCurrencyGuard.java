package com.jhanantezana.jugueria.instore.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.store.CurrencyChangeGuard;
import com.jhanantezana.jugueria.store.StoreError;

/** Open shifts and tickets hold money in the current currency, so it cannot change until they end. */
@Component
class OpenWorkCurrencyGuard implements CurrencyChangeGuard {

	private final JdbcClient jdbc;

	OpenWorkCurrencyGuard(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void requireCurrencyChangeAllowed() {
		var open = jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM instore.register_shift WHERE status = 'OPEN')
					OR EXISTS (SELECT 1 FROM instore.ticket WHERE status = 'OPEN')
				""").query(Boolean.class).single();
		if (open) {
			throw new BusinessException(StoreError.CURRENCY_LOCKED,
					"The currency cannot change while a register shift or a ticket is open");
		}
	}

}
