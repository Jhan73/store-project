package com.jhanantezana.jugueria.store;

import java.util.Currency;

import com.jhanantezana.jugueria.shared.Money;

public record StoreSettingsSnapshot(String timeZone, Currency currency, int basePrepMinutes,
		int queueMinutesPerOrder, int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes,
		Money registerDifferenceThreshold, int exceptionThreshold, int onlineCapacityLimit,
		boolean allowNegativeStockOnSale) {

	/** A snapshot of a store that rejects a sale the shelf cannot cover. */
	public StoreSettingsSnapshot(String timeZone, Currency currency, int basePrepMinutes, int queueMinutesPerOrder,
			int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes, Money registerDifferenceThreshold,
			int exceptionThreshold, int onlineCapacityLimit) {
		this(timeZone, currency, basePrepMinutes, queueMinutesPerOrder, busyModeMinutes, boardWarningMinutes,
				boardLateMinutes, registerDifferenceThreshold, exceptionThreshold, onlineCapacityLimit, false);
	}

}
