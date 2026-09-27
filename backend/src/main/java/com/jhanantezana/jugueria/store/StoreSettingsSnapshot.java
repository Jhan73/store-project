package com.jhanantezana.jugueria.store;

import java.util.Currency;

import com.jhanantezana.jugueria.shared.Money;

public record StoreSettingsSnapshot(String timeZone, Currency currency, int basePrepMinutes,
		int queueMinutesPerOrder, int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes,
		Money registerDifferenceThreshold, int exceptionThreshold, int onlineCapacityLimit) {
}
