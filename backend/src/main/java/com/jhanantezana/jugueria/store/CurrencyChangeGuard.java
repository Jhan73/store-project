package com.jhanantezana.jugueria.store;

/**
 * Lets the modules that hold work in progress veto a change of the store currency. Implemented as a bean by the
 * module that owns the work, so {@code store} never depends on it.
 */
public interface CurrencyChangeGuard {

	/** @throws com.jhanantezana.jugueria.shared.BusinessException with {@link StoreError#CURRENCY_LOCKED} */
	void requireCurrencyChangeAllowed();

}
