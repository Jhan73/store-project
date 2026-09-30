package com.jhanantezana.jugueria.store;

import com.jhanantezana.jugueria.shared.Money;

public record StoreThresholds(int basePrepMinutes, int queueMinutesPerOrder, int busyModeMinutes,
		int boardWarningMinutes, int boardLateMinutes, Money registerDifferenceThreshold, int exceptionThreshold,
		int onlineCapacityLimit) {
}
