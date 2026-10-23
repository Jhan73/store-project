package com.jhanantezana.jugueria.store.web;

import java.util.Currency;

import com.jhanantezana.jugueria.shared.Money;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** A full replacement: every field is sent, so an edit of one can never reset another by leaving it out. */
record UpdateStoreSettingsRequest(@NotBlank String timeZone, @NotNull Currency currency,
		@Positive int basePrepMinutes, @PositiveOrZero int queueMinutesPerOrder,
		@PositiveOrZero int busyModeMinutes, @Positive int boardWarningMinutes, @Positive int boardLateMinutes,
		@NotNull Money registerDifferenceThreshold, @Positive int exceptionThreshold,
		@Positive int onlineCapacityLimit, @NotNull @Schema(deprecated = true, description = "No longer affects sales; a sale is always recorded and stock may go negative.") Boolean allowNegativeStockOnSale) {
}
