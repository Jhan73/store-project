package com.jhanantezana.jugueria.store.web;

import java.util.Currency;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Money;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** {@code allowNegativeStockOnSale} left out means false, like every field of a full replacement. */
record UpdateStoreSettingsRequest(@NotBlank String timeZone, @NotNull Currency currency,
		@Positive int basePrepMinutes, @PositiveOrZero int queueMinutesPerOrder,
		@PositiveOrZero int busyModeMinutes, @Positive int boardWarningMinutes, @Positive int boardLateMinutes,
		@NotNull Money registerDifferenceThreshold, @Positive int exceptionThreshold,
		@Positive int onlineCapacityLimit,
		@Nullable @Schema(defaultValue = "false") Boolean allowNegativeStockOnSale) {

	boolean negativeStockAllowed() {
		return Boolean.TRUE.equals(allowNegativeStockOnSale);
	}

}
