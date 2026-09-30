package com.jhanantezana.jugueria.store.web;

import java.util.Currency;
import java.util.UUID;

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.store.internal.StoreSettings;

record StoreSettingsResponse(UUID id, String timeZone, Currency currency, int basePrepMinutes,
		int queueMinutesPerOrder, int busyModeMinutes, int boardWarningMinutes, int boardLateMinutes,
		Money registerDifferenceThreshold, int exceptionThreshold, int onlineCapacityLimit) {

	static StoreSettingsResponse from(StoreSettings settings) {
		return new StoreSettingsResponse(settings.getId(), settings.getTimeZone(), settings.getCurrency(),
				settings.getBasePrepMinutes(), settings.getQueueMinutesPerOrder(), settings.getBusyModeMinutes(),
				settings.getBoardWarningMinutes(), settings.getBoardLateMinutes(),
				settings.getRegisterDifferenceThreshold(), settings.getExceptionThreshold(),
				settings.getOnlineCapacityLimit());
	}

}
